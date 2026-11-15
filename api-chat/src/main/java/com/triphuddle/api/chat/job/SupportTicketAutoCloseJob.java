package com.triphuddle.api.chat.job;

import com.triphuddle.api.chat.config.ChatProperties;
import com.triphuddle.api.chat.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled job that closes support tickets left RESOLVED for longer than {@code app.chat.support.auto-close-after-days}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportTicketAutoCloseJob {

    private static final int BATCH_SIZE = 50;

    // Bounds one run; whatever is left waits for the next.
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final SupportTicketService supportTicketService;
    private final ChatProperties chatProperties;

    /**
     * Close the due tickets in batches, each in its own transaction.
     */
    @Scheduled(fixedDelayString = "${app.chat.support.auto-close-interval-ms}",
            initialDelayString = "${app.chat.support.auto-close-interval-ms}")
    public void closeResolvedTickets() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(chatProperties.support().autoCloseAfterDays()));
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int closed;
            try {
                closed = supportTicketService.autoCloseResolved(cutoff, BATCH_SIZE);
            } catch (RuntimeException e) {
                log.error("Support ticket auto-close batch failed [batch={}]", batch + 1, e);
                break;
            }
            total += closed;
            if (closed < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("Support tickets auto-closed [count={}, cutoff={}]", total, cutoff);
        }
    }
}
