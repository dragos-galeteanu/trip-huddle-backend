package com.triphuddle.api.destination.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.triphuddle.api.destination.config.DestinationProperties;
import com.triphuddle.api.destination.config.ImportProperties;
import com.triphuddle.api.destination.dto.PhotoResponse;
import com.triphuddle.api.destination.entity.PhotoSource;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlacePhoto;
import com.triphuddle.api.destination.opendata.CommonsClient;
import com.triphuddle.api.destination.opendata.CommonsImage;
import com.triphuddle.api.destination.repository.PlacePhotoRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Place photos from Wikimedia Commons: added by imports, copied to the bucket when one is configured, shown with
 * their attribution, and managed by staff.
 */
@Slf4j
@Service
public class PhotoService {

    private final PlacePhotoRepository photoRepository;
    private final PlaceRepository placeRepository;
    private final CommonsClient commonsClient;
    private final ImportProperties importProperties;
    private final DestinationProperties properties;
    private final ObjectProvider<Storage> storage;
    private final TransactionTemplate transactions;

    public PhotoService(PlacePhotoRepository photoRepository, PlaceRepository placeRepository,
                        CommonsClient commonsClient, ImportProperties importProperties,
                        DestinationProperties properties, ObjectProvider<Storage> storage,
                        TransactionTemplate transactions) {
        this.photoRepository = photoRepository;
        this.placeRepository = placeRepository;
        this.commonsClient = commonsClient;
        this.importProperties = importProperties;
        this.properties = properties;
        this.storage = storage;
        this.transactions = transactions;
    }

    /**
     * Add each place's Commons image, unless it has that file already, also when staff removed it. The first photo
     * of a place becomes its primary one. Each photo is saved on its own, so one failure loses only that photo.
     *
     * @param fileNamesByPlace the Commons file to add to each place
     * @return how many photos were added
     */
    public int addFromCommons(Map<UUID, String> fileNamesByPlace) {
        List<Map.Entry<UUID, String>> wanted = fileNamesByPlace.entrySet().stream()
                .filter(entry -> !photoRepository.hasFile(entry.getKey(), entry.getValue()))
                .toList();
        if (wanted.isEmpty()) {
            return 0;
        }
        Map<String, CommonsImage> images = commonsClient.images(
                wanted.stream().map(Map.Entry::getValue).distinct().toList(), importProperties.photoWidth());
        int added = 0;
        for (Map.Entry<UUID, String> entry : wanted) {
            CommonsImage image = images.get(entry.getValue());
            if (image == null) {
                continue;
            }
            try {
                String storagePath = copyToBucket(entry.getKey(), image);
                Boolean isAdded = transactions.execute(status -> add(entry.getKey(), image, storagePath));
                if (Boolean.TRUE.equals(isAdded)) {
                    added++;
                }
            } catch (RuntimeException e) {
                log.warn("Photo not added [placeId={}, file={}]", entry.getKey(), entry.getValue(), e);
            }
        }
        return added;
    }

    /**
     * Get a place's photos, the primary one first.
     *
     * @param placeId the ID of the place
     * @return the photos
     */
    public List<PhotoResponse> photosOf(UUID placeId) {
        return photoRepository.findForPlace(placeId).stream().map(this::toResponse).toList();
    }

    /**
     * Get the primary photos of some places.
     *
     * @param placeIds the IDs of the places
     * @return each place's primary photo, by place ID; places without one are left out
     */
    public Map<UUID, PhotoResponse> primaryPhotosOf(Collection<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return photoRepository.findPrimaryForPlaces(placeIds).stream()
                .collect(Collectors.toMap(photo -> photo.getPlace().getId(), this::toResponse));
    }

    /**
     * Make a photo its place's primary one.
     *
     * @param placeId the ID of the place
     * @param photoId the ID of the photo
     * @return the place's photos
     */
    public List<PhotoResponse> makePrimary(UUID placeId, UUID photoId) {
        transactions.executeWithoutResult(status -> {
            PlacePhoto photo = find(placeId, photoId);
            // The old primary lets go first, as only one may be primary at a time.
            photoRepository.findForPlace(placeId).stream().filter(PlacePhoto::isPrimary)
                    .forEach(old -> {
                        old.setPrimary(false);
                        photoRepository.saveAndFlush(old);
                    });
            photo.setPrimary(true);
        });
        return photosOf(placeId);
    }

    /**
     * Remove a photo; an import won't add its file to the place again. The next photo becomes primary.
     *
     * @param placeId the ID of the place
     * @param photoId the ID of the photo
     */
    public void delete(UUID placeId, UUID photoId) {
        transactions.executeWithoutResult(status -> {
            PlacePhoto photo = find(placeId, photoId);
            boolean wasPrimary = photo.isPrimary();
            photo.setPrimary(false);
            photo.setDeletedAt(Instant.now());
            photoRepository.saveAndFlush(photo);
            if (wasPrimary) {
                photoRepository.findForPlace(placeId).stream().findFirst().ifPresent(next -> next.setPrimary(true));
            }
        });
    }

    private boolean add(UUID placeId, CommonsImage image, String storagePath) {
        Place place = placeRepository.findByIdAndDeletedAtIsNull(placeId).orElse(null);
        if (place == null || photoRepository.hasFile(placeId, image.fileName())) {
            return false;
        }
        PlacePhoto photo = new PlacePhoto();
        photo.setPlace(place);
        photo.setSource(PhotoSource.WIKIMEDIA_COMMONS);
        photo.setSourceFileName(image.fileName());
        photo.setSourceUrl(image.url());
        photo.setStoragePath(storagePath);
        photo.setWidth(image.width());
        photo.setHeight(image.height());
        photo.setMimeType(image.mimeType());
        photo.setAuthor(image.author());
        photo.setLicenseName(truncate(image.licenseName(), 64));
        photo.setLicenseUrl(image.licenseUrl());
        photo.setSourcePageUrl(image.sourcePageUrl());
        photo.setAttributionRequired(image.isAttributionRequired());
        photo.setPrimary(!photoRepository.hasPrimary(placeId));
        photoRepository.save(photo);
        return true;
    }

    // Without a bucket the photo is shown from Commons, which allows that; with one, a copy is kept.
    private String copyToBucket(UUID placeId, CommonsImage image) {
        Storage client = storage.getIfAvailable();
        if (client == null || !properties.photos().isEnabled()) {
            return null;
        }
        String extension = image.mimeType() != null && image.mimeType().contains("png") ? "png"
                : image.mimeType() != null && image.mimeType().contains("webp") ? "webp" : "jpg";
        String path = "places/%s/%s.%s".formatted(placeId, UUID.randomUUID(), extension);
        byte[] bytes = commonsClient.download(image.url());
        client.create(BlobInfo.newBuilder(properties.photos().bucket(), path)
                .setContentType(image.mimeType() == null ? "image/jpeg" : image.mimeType())
                .setCacheControl("public, max-age=31536000")
                .build(), bytes);
        return path;
    }

    private PhotoResponse toResponse(PlacePhoto photo) {
        return new PhotoResponse(photo.getId(), urlOf(photo), photo.getWidth(), photo.getHeight(), photo.getAuthor(),
                photo.getLicenseName(), photo.getLicenseUrl(), photo.getSourcePageUrl(),
                photo.isAttributionRequired(), photo.isPrimary());
    }

    private String urlOf(PlacePhoto photo) {
        Storage client = storage.getIfAvailable();
        if (photo.getStoragePath() == null || client == null) {
            return photo.getSourceUrl();
        }
        BlobInfo blob = BlobInfo.newBuilder(BlobId.of(properties.photos().bucket(), photo.getStoragePath())).build();
        return client.signUrl(blob, Math.max(properties.photos().downloadUrlTtlMinutes(), 1), TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET), Storage.SignUrlOption.withV4Signature()).toString();
    }

    private PlacePhoto find(UUID placeId, UUID photoId) {
        return photoRepository.findByIdAndDeletedAtIsNull(photoId)
                .filter(photo -> photo.getPlace().getId().equals(placeId))
                .orElseThrow(() -> new ResourceNotFoundException("Photo with id=%s not found".formatted(photoId)));
    }

    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
