package com.triphuddle.api.destination.content;

import com.triphuddle.api.destination.dto.ContentReportResponse;
import com.triphuddle.api.destination.entity.ContentReport;
import com.triphuddle.api.destination.entity.ContentSection;
import com.triphuddle.api.destination.entity.GenerationPriority;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceContent;
import com.triphuddle.api.destination.repository.ContentReportRepository;
import com.triphuddle.api.destination.repository.PlaceContentRepository;
import com.triphuddle.api.destination.service.PlaceQueryService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Readers report sections that are wrong or unsuitable; staff keep or regenerate them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentReportService {

    private final ContentReportRepository reportRepository;
    private final PlaceContentRepository contentRepository;
    private final PlaceQueryService placeQueryService;
    private final ContentGenerator generator;
    private final Clock clock;

    /**
     * Report a section a reader saw.
     *
     * @param placeId the ID of the place
     * @param section the section
     * @param languageCode the language read
     * @param reason why
     * @param userId the reader
     * @return the report
     */
    public ContentReportResponse report(UUID placeId, ContentSection section, String languageCode, String reason,
                                        UUID userId) {
        Place place = placeQueryService.visiblePlace(placeId, false);
        PlaceContent content = contentRepository.lock(place.getId(), section, ContentService.normalise(languageCode))
                .filter(PlaceContent::hasContent)
                .orElseThrow(() -> new ResourceNotFoundException("No %s content to report".formatted(section)));
        ContentReport report = new ContentReport();
        report.setContent(content);
        report.setReportedByUserId(userId);
        report.setReason(reason.strip());
        ContentReport saved = reportRepository.save(report);
        log.info("Content reported [reportId={}, contentId={}, userId={}]", saved.getId(), content.getId(), userId);
        return toResponse(saved);
    }

    /**
     * Get reports for staff, oldest first.
     *
     * @param isOpen true for open reports, false for resolved ones
     * @param filter the page
     * @return a page of reports
     */
    @Transactional(readOnly = true)
    public Paginated<ContentReportResponse> getAll(boolean isOpen, PaginationFilter filter) {
        return Paginated.of(reportRepository.findForStaff(isOpen, filter.toPageable()),
                reports -> reports.stream().map(ContentReportService::toResponse).toList());
    }

    /**
     * Resolve a report: keep the section as it is, or regenerate it now.
     *
     * @param reportId the ID of the report
     * @param isRegenerating true to regenerate it
     * @param staffId who resolved it
     * @return the report
     */
    public ContentReportResponse resolve(UUID reportId, boolean isRegenerating, UUID staffId) {
        ContentReport report = reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report with id=%s not found".formatted(reportId)));
        if (report.getResolution() != null) {
            throw new IllegalStateException("Report with id=%s is already resolved".formatted(reportId));
        }
        if (isRegenerating) {
            PlaceContent content = contentRepository.lock(report.getContent().getPlace().getId(),
                    report.getContent().getSection(), report.getContent().getLanguageCode()).orElseThrow();
            generator.request(content, placeQueryService.ancestorsOf(content.getPlace()), GenerationPriority.STAFF,
                    null);
        }
        report.setResolution(isRegenerating ? "REGENERATED" : "KEPT");
        report.setResolvedByUserId(staffId);
        report.setResolvedAt(clock.instant());
        log.info("Content report resolved [reportId={}, resolution={}, staffId={}]", reportId,
                report.getResolution(), staffId);
        return toResponse(report);
    }

    private static ContentReportResponse toResponse(ContentReport report) {
        PlaceContent content = report.getContent();
        return new ContentReportResponse(report.getId(), content.getPlace().getId(), content.getPlace().getName(),
                content.getSection(), content.getLanguageCode(), report.getReason(), report.getReportedByUserId(),
                report.getCreatedAt(), report.getResolution(), report.getResolvedByUserId(), report.getResolvedAt());
    }
}
