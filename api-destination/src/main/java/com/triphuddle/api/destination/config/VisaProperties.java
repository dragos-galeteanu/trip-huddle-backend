package com.triphuddle.api.destination.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for visa rules and guidance, under {@code app.destination.visas}.
 *
 * @param datasetUrl the passport-index CSV with ISO 3166-1 alpha-2 codes
 * @param minRulesForFullImport an import with fewer rows removes nothing, as the download may be cut short
 * @param guidanceFreshDays how long guidance is served before it is written again
 * @param retryFailedAfterMinutes how long a failed guidance waits before a read tries again
 * @param pendingTimeoutMinutes how late an answer may be before the guidance is given up on
 * @param runningTimeoutMinutes how long an import may run before it is taken for dead
 */
@ConfigurationProperties(prefix = "app.destination.visas")
public record VisaProperties(
        String datasetUrl,
        int minRulesForFullImport,
        int guidanceFreshDays,
        int retryFailedAfterMinutes,
        int pendingTimeoutMinutes,
        int runningTimeoutMinutes
) {
}
