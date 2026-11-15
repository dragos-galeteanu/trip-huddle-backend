package com.triphuddle.api.destination.job;

import com.triphuddle.api.destination.social.QuestionService;
import com.triphuddle.api.destination.semantic.SemanticSearchService;
import com.triphuddle.api.destination.visa.VisaGuidanceService;
import com.triphuddle.api.destination.content.ContentMaintenance;
import com.triphuddle.api.destination.trip.ItineraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that refreshes aged content people still read, sends again content jobs whose answer never came,
 * gives up on itineraries, visa guidance and questions whose answer never came, and embeds places for semantic
 * search, a batch a run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentJobs {

    private final ContentMaintenance maintenance;
    private final ItineraryService itineraryService;
    private final VisaGuidanceService visaGuidanceService;
    private final QuestionService questionService;
    private final SemanticSearchService semanticSearchService;

    /**
     * Run one batch of each.
     */
    @Scheduled(fixedDelayString = "${app.destination.content.refresh-interval-ms:600000}",
            initialDelayString = "${app.destination.content.refresh-interval-ms:600000}")
    public void run() {
        try {
            int resent = maintenance.resendOverdue();
            int refreshed = maintenance.refreshDue();
            int itinerariesGivenUp = itineraryService.failOverdue();
            int visasGivenUp = visaGuidanceService.giveUpOverdue();
            int questionsGivenUp = questionService.giveUpOverdue();
            if (resent + refreshed + itinerariesGivenUp + visasGivenUp + questionsGivenUp > 0) {
                log.info("Content maintenance [resent={}, refreshed={}, itinerariesGivenUp={}, visasGivenUp={},"
                        + " questionsGivenUp={}]", resent, refreshed, itinerariesGivenUp, visasGivenUp,
                        questionsGivenUp);
            }
        } catch (RuntimeException e) {
            log.error("Content maintenance failed", e);
        }
        try {
            int released = semanticSearchService.releaseOverdue();
            int embedding = semanticSearchService.embedDue();
            if (released + embedding > 0) {
                log.info("Embedding maintenance [released={}, sent={}]", released, embedding);
            }
        } catch (RuntimeException e) {
            log.error("Embedding maintenance failed", e);
        }
    }
}
