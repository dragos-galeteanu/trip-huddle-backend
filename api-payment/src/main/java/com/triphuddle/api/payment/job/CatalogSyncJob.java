package com.triphuddle.api.payment.job;

import com.triphuddle.api.payment.service.CatalogSyncException;
import com.triphuddle.api.payment.service.CatalogSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled job that pushes changed licenses to Stripe, one transaction per license.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSyncJob {

    // Bounds one run; whatever is left waits for the next.
    private static final int MAX_LICENSES_PER_RUN = 50;

    private final CatalogSyncService catalogSyncService;

    /**
     * Sync due licenses until none is left or the run's bound is reached.
     */
    @Scheduled(fixedDelayString = "${app.payment.catalog-sync-interval-ms}",
            initialDelayString = "${app.payment.catalog-sync-interval-ms}")
    public void syncDue() {
        Instant now = Instant.now();
        int total = 0;
        for (int i = 0; i < MAX_LICENSES_PER_RUN; i++) {
            Optional<UUID> synced;
            try {
                synced = catalogSyncService.syncNext(now);
            } catch (CatalogSyncException e) {
                catalogSyncService.recordFailure(e.getLicenseId(), e.getCause(), now);
                continue;
            } catch (RuntimeException e) {
                log.error("License sync run failed [synced={}]", total, e);
                break;
            }
            if (synced.isEmpty()) {
                break;
            }
            total++;
        }
        if (total > 0) {
            log.info("Licenses synced to Stripe [count={}]", total);
        }
    }
}
