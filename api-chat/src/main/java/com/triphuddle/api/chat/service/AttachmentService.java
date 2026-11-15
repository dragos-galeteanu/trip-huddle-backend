package com.triphuddle.api.chat.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.triphuddle.api.chat.config.ChatProperties;
import com.triphuddle.api.chat.dto.AttachmentRequest;
import com.triphuddle.api.chat.dto.CreateUploadUrlRequest;
import com.triphuddle.api.chat.dto.UploadUrlResponse;
import com.triphuddle.api.chat.entity.MessageAttachment;
import com.triphuddle.common.config.StorageBucketProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Hands out signed upload and download URLs for attachments and checks uploads before they are attached.
 */
@Slf4j
@Service
public class AttachmentService {

    private static final String CONTENT_LENGTH_RANGE_HEADER = "x-goog-content-length-range";

    private final Storage storage;
    private final StorageBucketProperties settings;

    public AttachmentService(ObjectProvider<Storage> storageProvider, ChatProperties chatProperties) {
        // No bucket configured means no Storage bean: the service still runs, only without attachments.
        this.storage = storageProvider.getIfAvailable();
        this.settings = chatProperties.attachments();
    }

    /**
     * Create a signed URL to upload one file for a conversation.
     *
     * @param conversationId the ID of the conversation
     * @param uploaderId the ID of the user uploading
     * @param request the name, type and size of the file
     * @return the upload URL and the object path to attach afterwards
     * @throws IllegalStateException if attachments are not configured or the file is too large
     */
    public UploadUrlResponse createUploadUrl(UUID conversationId, UUID uploaderId, CreateUploadUrlRequest request) {
        requireEnabled();
        if (request.getSizeBytes() > settings.maxSizeBytes()) {
            throw new IllegalStateException("Attachments cannot exceed %d bytes".formatted(settings.maxSizeBytes()));
        }

        // The uploader is part of the path so that a message can only attach its own sender's uploads.
        String objectPath = pathPrefix(conversationId, uploaderId) + UUID.randomUUID();
        BlobInfo blobInfo = BlobInfo.newBuilder(settings.bucket(), objectPath)
                .setContentType(request.getContentType())
                .build();

        // GCS rejects the upload unless its size falls in this range, so the declared size is enforced.
        Map<String, String> requiredHeaders = new LinkedHashMap<>();
        requiredHeaders.put("Content-Type", request.getContentType());
        requiredHeaders.put(CONTENT_LENGTH_RANGE_HEADER, "0," + request.getSizeBytes());

        URL url = storage.signUrl(
                blobInfo,
                settings.uploadUrlTtlMinutes(),
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withContentType(),
                Storage.SignUrlOption.withExtHeaders(Map.of(CONTENT_LENGTH_RANGE_HEADER, "0," + request.getSizeBytes()))
        );

        log.info("Attachment upload URL created [conversationId={}, uploaderId={}, objectPath={}]",
                conversationId, uploaderId, objectPath);
        return new UploadUrlResponse(
                url.toString(),
                HttpMethod.PUT.name(),
                requiredHeaders,
                objectPath,
                Instant.now().plus(settings.uploadUrlTtlMinutes(), ChronoUnit.MINUTES)
        );
    }

    /**
     * Check that a file was uploaded by the sender for this conversation, and describe it.
     *
     * @param conversationId the ID of the conversation
     * @param senderId the ID of the sender
     * @param request the object path and file name sent with the message
     * @return the attachment to store with the message
     * @throws IllegalStateException if the file is missing, someone else's or too large
     */
    public MessageAttachment resolveUploaded(UUID conversationId, UUID senderId, AttachmentRequest request) {
        requireEnabled();
        String objectPath = request.getObjectPath();
        if (!objectPath.startsWith(pathPrefix(conversationId, senderId))) {
            throw new IllegalStateException("Attachment %s was not uploaded by you for this conversation"
                    .formatted(objectPath));
        }

        Blob blob = storage.get(BlobId.of(settings.bucket(), objectPath));
        if (blob == null) {
            throw new IllegalStateException("Attachment %s has not been uploaded".formatted(objectPath));
        }
        if (blob.getSize() > settings.maxSizeBytes()) {
            throw new IllegalStateException("Attachments cannot exceed %d bytes".formatted(settings.maxSizeBytes()));
        }

        return new MessageAttachment(objectPath, request.getFileName(), blob.getContentType(), blob.getSize());
    }

    /**
     * Create a short-lived signed URL to download an attachment.
     *
     * @param objectPath the path of the file in the bucket
     * @return the URL, or empty if attachments are not configured or signing failed
     */
    public Optional<String> createDownloadUrl(String objectPath) {
        if (storage == null) {
            return Optional.empty();
        }
        try {
            URL url = storage.signUrl(
                    BlobInfo.newBuilder(settings.bucket(), objectPath).build(),
                    settings.downloadUrlTtlMinutes(),
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature()
            );
            return Optional.of(url.toString());
        } catch (StorageException | IllegalStateException e) {
            // One unsignable attachment must not break the whole history page.
            log.warn("Could not sign attachment download URL [objectPath={}]", objectPath, e);
            return Optional.empty();
        }
    }

    private void requireEnabled() {
        if (storage == null || !settings.isEnabled()) {
            throw new IllegalStateException("Attachments are not enabled on this server");
        }
    }

    private static String pathPrefix(UUID conversationId, UUID userId) {
        return "chats/%s/%s/".formatted(conversationId, userId);
    }
}
