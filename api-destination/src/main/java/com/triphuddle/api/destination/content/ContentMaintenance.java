package com.triphuddle.api.destination.content;

import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.entity.GenerationPriority;
import com.triphuddle.api.destination.entity.PlaceContent;
import com.triphuddle.api.destination.repository.PlaceContentRepository;
import com.triphuddle.api.destination.service.PlaceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Keeps content fresh and moving: refreshes aged sections that people still read, and sends again the jobs whose
 * answer never came.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentMaintenance {

    private final PlaceContentRepository contentRepository;
    private final PlaceQueryService placeQueryService;
    private final ContentGenerator generator;
    private final ContentProperties properties;
    private final Clock clock;

    /**
     * Refresh one batch of aged sections read lately; it stops at the day's budget.
     *
     * @return how many were sent
     */
    public int refreshDue() {
        Instant now = clock.instant();
        List<PlaceContent> due = contentRepository.lockDueForRefresh(now,
                now.minus(Duration.ofDays(properties.refreshViewedWithinDays())),
                PageRequest.of(0, properties.refreshBatchSize()));
        int sent = 0;
        for (PlaceContent content : due) {
            if (content.getPlace().getDeletedAt() != null) {
                continue;
            }
            ContentGenerator.Refusal refusal = generator.request(content,
                    placeQueryService.ancestorsOf(content.getPlace()), GenerationPriority.REFRESH, null);
            if (refusal == ContentGenerator.Refusal.BUDGET) {
                break;
            }
            sent++;
        }
        return sent;
    }

    /**
     * Send again one batch of jobs whose answer is overdue, with the priority they had.
     *
     * @return how many were sent again
     */
    public int resendOverdue() {
        Instant now = clock.instant();
        List<PlaceContent> overdue = contentRepository.lockOverdue(
                now.minus(Duration.ofMinutes(properties.pendingTimeoutMinutes())),
                PageRequest.of(0, properties.refreshBatchSize()));
        for (PlaceContent content : overdue) {
            GenerationPriority priority = content.getPendingPriority() == null ? GenerationPriority.REFRESH
                    : content.getPendingPriority();
            log.warn("Answer overdue, sending again [contentId={}, jobId={}]", content.getId(),
                    content.getPendingJobId());
            // A late answer to the old job is then ignored.
            content.setPendingJobId(null);
            // The reader's share was counted when it was first sent.
            generator.request(content, placeQueryService.ancestorsOf(content.getPlace()),
                    priority == GenerationPriority.VIEW ? GenerationPriority.REFRESH : priority, null);
        }
        return overdue.size();
    }
}
