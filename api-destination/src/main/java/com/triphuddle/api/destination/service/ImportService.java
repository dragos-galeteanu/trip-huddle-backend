package com.triphuddle.api.destination.service;

import com.triphuddle.api.destination.config.ImportProperties;
import com.triphuddle.api.destination.dto.ImportJobResponse;
import com.triphuddle.api.destination.dto.ImportRequest;
import com.triphuddle.api.destination.dto.PlaceNameRequest;
import com.triphuddle.api.destination.dto.PlaceRequest;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.ImportJob;
import com.triphuddle.api.destination.entity.ImportStatus;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceKind;
import com.triphuddle.api.destination.entity.PlaceName;
import com.triphuddle.api.destination.opendata.WikidataClient;
import com.triphuddle.api.destination.opendata.WikidataCountry;
import com.triphuddle.api.destination.opendata.WikidataItem;
import com.triphuddle.api.destination.opendata.WikidataLabel;
import com.triphuddle.api.destination.repository.CategoryRepository;
import com.triphuddle.api.destination.repository.CountryCopyRepository;
import com.triphuddle.api.destination.repository.ImportJobRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.api.destination.repository.PlaceSearchRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports a country from Wikidata: its regions, its cities above a population, the notable places of every
 * Wikidata class a category maps, and their Wikimedia Commons photos. Places are matched by Wikidata ID, so a
 * second run updates rather than duplicates; what staff set, such as names, kinds and parents, is left alone.
 */
@Slf4j
@Service
public class ImportService {

    private static final int BATCH = 50;

    private final ImportJobRepository jobRepository;
    private final PlaceRepository placeRepository;
    private final CategoryRepository categoryRepository;
    private final CountryCopyRepository countryCopyRepository;
    private final PlaceSearchRepository placeSearchRepository;
    private final PlaceAdminService placeAdminService;
    private final PhotoService photoService;
    private final WikidataClient wikidata;
    private final ImportProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ImportService(ImportJobRepository jobRepository, PlaceRepository placeRepository,
                         CategoryRepository categoryRepository, CountryCopyRepository countryCopyRepository,
                         PlaceSearchRepository placeSearchRepository, PlaceAdminService placeAdminService,
                         PhotoService photoService, WikidataClient wikidata, ImportProperties properties,
                         TransactionTemplate transactions, Clock clock) {
        this.jobRepository = jobRepository;
        this.placeRepository = placeRepository;
        this.categoryRepository = categoryRepository;
        this.countryCopyRepository = countryCopyRepository;
        this.placeSearchRepository = placeSearchRepository;
        this.placeAdminService = placeAdminService;
        this.photoService = photoService;
        this.wikidata = wikidata;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Queue an import of an active country; one country has at most one waiting or running import.
     *
     * @param request the country and what to include
     * @param userId the staff member asking
     * @return the queued import
     */
    public ImportJobResponse request(ImportRequest request, UUID userId) {
        String countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
        return transactions.execute(status -> {
            if (!countryCopyRepository.existsByAlpha2AndDisabledAtIsNullAndDeletedAtIsNull(countryCode)) {
                throw new IllegalStateException("Country %s is not an active country".formatted(countryCode));
            }
            if (jobRepository.isActive(countryCode)) {
                throw new IllegalStateException("An import of %s is already waiting or running".formatted(countryCode));
            }
            ImportJob job = new ImportJob();
            job.setCountryCode(countryCode);
            job.setRequestedByUserId(userId);
            job.setMinCityPopulation(request.getMinCityPopulation() == null
                    ? properties.minCityPopulation() : request.getMinCityPopulation());
            job.setMinPoiSitelinks(request.getMinPoiSitelinks() == null
                    ? properties.minPoiSitelinks() : request.getMinPoiSitelinks());
            job.setIncludingPois(!Boolean.FALSE.equals(request.getIsIncludingPois()));
            job.setIncludingPhotos(!Boolean.FALSE.equals(request.getIsIncludingPhotos()));
            ImportJob saved = jobRepository.saveAndFlush(job);
            log.info("Import queued [id={}, countryCode={}, userId={}]", saved.getId(), countryCode, userId);
            return toResponse(saved);
        });
    }

    /**
     * Take the next waiting import, or one an instance stopped running, and run it to the end.
     *
     * @return the ID of the import run, or null if none was waiting
     */
    public UUID runNext() {
        Instant now = clock.instant();
        UUID jobId = transactions.execute(status ->
                jobRepository.claimNext(now.minusSeconds(15 * 60), now).orElse(null));
        if (jobId != null) {
            run(jobId);
        }
        return jobId;
    }

    /**
     * Run an import; a failure is recorded on it, and what was imported until then stays.
     *
     * @param jobId the ID of the import
     */
    public void run(UUID jobId) {
        ImportJob job = jobRepository.findById(jobId).orElseThrow();
        try {
            WikidataCountry country = wikidata.findCountry(job.getCountryCode())
                    .orElseThrow(() -> new IllegalStateException("Wikidata has no country with code "
                            + job.getCountryCode()));
            update(jobId, saved -> saved.setCountryWikidataId(country.id()));
            List<String> languages = new ArrayList<>(new LinkedHashSet<>(concat(properties.languages(),
                    country.languages())));
            Map<UUID, String> photos = new LinkedHashMap<>();

            stage(jobId, "REGIONS");
            List<WikidataItem> regions = wikidata.regions(country.id());
            Map<String, UUID> regionPlaces = upsert(job, regions, PlaceKind.REGION, languages,
                    country.languages(), item -> null, null, photos);

            stage(jobId, "CITIES");
            List<WikidataItem> cities = wikidata.cities(country.id(), job.getMinCityPopulation(),
                    regionPlaces.keySet());
            upsert(job, cities, PlaceKind.CITY, languages, country.languages(),
                    item -> item.parentIds().stream().map(regionPlaces::get).filter(id -> id != null)
                            .findFirst().orElse(null), null, photos);

            if (job.isIncludingPois()) {
                stage(jobId, "PLACES");
                for (Category category : mappedCategories()) {
                    for (String classId : category.getWikidataClassIds()) {
                        List<WikidataItem> places = wikidata.placesOfClass(country.id(), classId,
                                job.getMinPoiSitelinks(), properties.maxPoisPerClass());
                        upsert(job, places, PlaceKind.POI, languages, country.languages(),
                                item -> nearestCity(job.getCountryCode(), item), category.getId(), photos);
                    }
                }
            }

            if (job.isIncludingPhotos() && !photos.isEmpty()) {
                stage(jobId, "PHOTOS");
                List<Map.Entry<UUID, String>> entries = new ArrayList<>(photos.entrySet());
                for (int from = 0; from < entries.size(); from += BATCH) {
                    Map<UUID, String> batch = entries.subList(from, Math.min(from + BATCH, entries.size())).stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    int added = photoService.addFromCommons(batch);
                    update(jobId, saved -> saved.setPhotosAdded(saved.getPhotosAdded() + added));
                }
            }

            update(jobId, saved -> {
                saved.setStatus(ImportStatus.COMPLETED);
                saved.setStage(null);
                saved.setFinishedAt(clock.instant());
            });
            log.info("Import completed [id={}, countryCode={}]", jobId, job.getCountryCode());
        } catch (RuntimeException e) {
            log.error("Import failed [id={}, countryCode={}]", jobId, job.getCountryCode(), e);
            update(jobId, saved -> {
                saved.setStatus(ImportStatus.FAILED);
                saved.setError(String.valueOf(e.getMessage()));
                saved.setFinishedAt(clock.instant());
            });
        }
    }

    /**
     * Get imports for staff, newest first.
     *
     * @param countryCode the country, or null for all
     * @param status the status, or null for all
     * @param filter the page
     * @return a page of imports
     */
    public Paginated<ImportJobResponse> getAll(String countryCode, ImportStatus status, PaginationFilter filter) {
        return transactions.execute(tx -> Paginated.of(jobRepository.findForStaff(
                        countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT), status, filter.toPageable()),
                jobs -> jobs.stream().map(ImportService::toResponse).toList()));
    }

    /**
     * Get an import.
     *
     * @param id the ID of the import
     * @return the import
     */
    public ImportJobResponse getById(UUID id) {
        return transactions.execute(tx -> toResponse(jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import with id=%s not found".formatted(id)))));
    }

    // Creates or updates the items in batches, each in its own transaction; returns their places by Wikidata ID.
    private Map<String, UUID> upsert(ImportJob job, List<WikidataItem> items, PlaceKind kind, List<String> languages,
                                     List<String> localLanguages, Function<WikidataItem, UUID> parentOf,
                                     UUID categoryId, Map<UUID, String> photos) {
        Map<String, List<WikidataLabel>> labels = wikidata.labels(items.stream().map(WikidataItem::id).toList(),
                languages).stream().collect(Collectors.groupingBy(WikidataLabel::itemId));
        Map<String, UUID> places = new HashMap<>();
        for (int from = 0; from < items.size(); from += BATCH) {
            List<WikidataItem> batch = items.subList(from, Math.min(from + BATCH, items.size()));
            int[] counts = new int[2];
            transactions.executeWithoutResult(status -> {
                for (WikidataItem item : batch) {
                    List<WikidataLabel> names = labels.getOrDefault(item.id(), List.of());
                    String name = nameOf(names, localLanguages);
                    if (name == null) {
                        continue;
                    }
                    Place existing = placeRepository.findByWikidataId(item.id()).orElse(null);
                    UUID placeId;
                    if (existing != null) {
                        if (existing.getDeletedAt() != null) {
                            continue;
                        }
                        refresh(existing, item, names, categoryId);
                        placeId = existing.getId();
                        counts[1]++;
                    } else {
                        placeId = placeAdminService.create(newPlace(job.getCountryCode(), kind, name, item, names,
                                parentOf.apply(item), categoryId)).id();
                        counts[0]++;
                    }
                    places.put(item.id(), placeId);
                    if (item.imageFileName() != null) {
                        photos.putIfAbsent(placeId, item.imageFileName());
                    }
                }
            });
            update(job.getId(), saved -> {
                saved.setPlacesCreated(saved.getPlacesCreated() + counts[0]);
                saved.setPlacesUpdated(saved.getPlacesUpdated() + counts[1]);
            });
        }
        return places;
    }

    // Wikidata's facts that staff rarely set: coordinates when missing, population, the OSM ID, extra names and
    // the category. Name, kind, parent, slug and visibility stay as they are.
    private void refresh(Place place, WikidataItem item, List<WikidataLabel> names, UUID categoryId) {
        if (place.getLatitude() == null && item.latitude() != null) {
            place.setLatitude(item.latitude());
            place.setLongitude(item.longitude());
        }
        if (item.population() != null) {
            place.setPopulation(item.population());
        }
        String osmId = osmIdOf(item);
        if (place.getOsmId() == null && osmId != null && !placeRepository.isOsmIdTaken(osmId, place.getId())) {
            place.setOsmId(osmId);
        }
        for (WikidataLabel label : names) {
            boolean isKnown = place.getNames().stream().anyMatch(name ->
                    name.getLanguageCode().equals(label.languageCode()) && name.getName().equals(label.label()));
            if (!isKnown && label.label().length() <= 200) {
                PlaceName added = new PlaceName();
                added.setPlace(place);
                added.setLanguageCode(label.languageCode());
                added.setName(label.label());
                place.getNames().add(added);
            }
        }
        if (categoryId != null) {
            categoryRepository.findByIdAndDeletedAtIsNull(categoryId).ifPresent(place.getCategories()::add);
        }
    }

    private PlaceRequest newPlace(String countryCode, PlaceKind kind, String name, WikidataItem item,
                                  List<WikidataLabel> names, UUID parentId, UUID categoryId) {
        PlaceRequest request = new PlaceRequest();
        request.setCountryCode(countryCode);
        request.setParentId(parentId);
        request.setKind(kind);
        request.setName(name);
        request.setLatitude(item.latitude());
        request.setLongitude(item.longitude());
        request.setWikidataId(item.id());
        String osmId = osmIdOf(item);
        request.setOsmId(osmId != null && !placeRepository.isOsmIdTaken(osmId, null) ? osmId : null);
        request.setPopulation(item.population());
        request.setNames(names.stream()
                .filter(label -> label.label().length() <= 200 && label.languageCode().matches("^[a-z]{2}$"))
                .map(label -> new PlaceNameRequest(label.languageCode(), label.label(), false))
                .toList());
        request.setCategoryIds(categoryId == null ? List.of() : List.of(categoryId));
        return request;
    }

    // English if it has one, else the country's own language, else any; null if Wikidata names it nowhere.
    static String nameOf(List<WikidataLabel> labels, List<String> localLanguages) {
        Map<String, String> byLanguage = labels.stream()
                .filter(label -> label.label() != null && !label.label().isBlank() && label.label().length() <= 200)
                .collect(Collectors.toMap(WikidataLabel::languageCode, WikidataLabel::label, (a, b) -> a));
        if (byLanguage.containsKey("en")) {
            return byLanguage.get("en");
        }
        for (String language : localLanguages) {
            if (byLanguage.containsKey(language)) {
                return byLanguage.get(language);
            }
        }
        return byLanguage.values().stream().findFirst().orElse(null);
    }

    private UUID nearestCity(String countryCode, WikidataItem item) {
        if (item.latitude() == null) {
            return null;
        }
        return placeSearchRepository.nearestCity(countryCode, item.latitude(), item.longitude(),
                properties.poiCityRadiusKm() * 1000);
    }

    private List<Category> mappedCategories() {
        return transactions.execute(status -> categoryRepository.findTree().stream()
                .filter(category -> !category.getWikidataClassIds().isEmpty())
                .peek(category -> category.getWikidataClassIds().size())
                .toList());
    }

    private static String osmIdOf(WikidataItem item) {
        return item.osmRelationId() != null && item.osmRelationId().matches("^[0-9]+$")
                ? "relation/" + item.osmRelationId() : null;
    }

    private void stage(UUID jobId, String stage) {
        update(jobId, saved -> saved.setStage(stage));
    }

    private void update(UUID jobId, Consumer<ImportJob> change) {
        transactions.executeWithoutResult(status -> jobRepository.findById(jobId).ifPresent(job -> {
            change.accept(job);
            job.setHeartbeatAt(clock.instant());
        }));
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(job.getId(), job.getCountryCode(), job.getStatus(), job.getStage(),
                job.getMinCityPopulation(), job.getMinPoiSitelinks(), job.isIncludingPois(), job.isIncludingPhotos(),
                job.getCountryWikidataId(), job.getPlacesCreated(), job.getPlacesUpdated(), job.getPhotosAdded(),
                job.getError(), job.getRequestedByUserId(), job.getCreatedAt(), job.getStartedAt(),
                job.getFinishedAt());
    }
}
