package com.triphuddle.api.destination.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.destination.content}, for AI content of places; non-positive values fall back to
 * defaults.
 *
 * @param overviewFreshDays how long an overview is served before it is refreshed
 * @param opinionsFreshDays how long what people say is served before it is refreshed
 * @param practicalFreshDays how long practical information is served before it is refreshed
 * @param refreshViewedWithinDays only content read within this many days is refreshed ahead; the rest on its next read
 * @param dailyTokenBudget tokens a UTC day may spend before refreshes and staff requests wait
 * @param dailyTokenHardLimit tokens a UTC day may spend before generation for readers waits too
 * @param maxGenerationsPerUserPerDay how many generations one user's reads may start per UTC day
 * @param retryFailedAfterMinutes how long a failed section waits before a read tries again
 * @param pendingTimeoutMinutes how long an answer may take before the job is sent again
 * @param refreshIntervalMs how often the refresh job runs
 * @param refreshBatchSize how many sections one refresh run sends
 */
@ConfigurationProperties(prefix = "app.destination.content")
public record ContentProperties(
        int overviewFreshDays,
        int opinionsFreshDays,
        int practicalFreshDays,
        int refreshViewedWithinDays,
        long dailyTokenBudget,
        long dailyTokenHardLimit,
        int maxGenerationsPerUserPerDay,
        int retryFailedAfterMinutes,
        int pendingTimeoutMinutes,
        long refreshIntervalMs,
        int refreshBatchSize
) {

    public ContentProperties {
        overviewFreshDays = overviewFreshDays <= 0 ? 180 : overviewFreshDays;
        opinionsFreshDays = opinionsFreshDays <= 0 ? 30 : opinionsFreshDays;
        practicalFreshDays = practicalFreshDays <= 0 ? 90 : practicalFreshDays;
        refreshViewedWithinDays = refreshViewedWithinDays <= 0 ? 30 : refreshViewedWithinDays;
        dailyTokenBudget = dailyTokenBudget <= 0 ? 2_000_000 : dailyTokenBudget;
        dailyTokenHardLimit = dailyTokenHardLimit <= 0 ? 5_000_000 : Math.max(dailyTokenHardLimit, dailyTokenBudget);
        maxGenerationsPerUserPerDay = maxGenerationsPerUserPerDay <= 0 ? 30 : maxGenerationsPerUserPerDay;
        retryFailedAfterMinutes = retryFailedAfterMinutes <= 0 ? 60 : retryFailedAfterMinutes;
        pendingTimeoutMinutes = pendingTimeoutMinutes <= 0 ? 30 : pendingTimeoutMinutes;
        refreshIntervalMs = refreshIntervalMs <= 0 ? 600_000 : refreshIntervalMs;
        refreshBatchSize = refreshBatchSize <= 0 ? 50 : refreshBatchSize;
    }
}
