package com.triphuddle.api.destination.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for semantic search, under {@code app.destination.search}.
 *
 * @param embeddingModel the embedding model asked for, or null for the worker's own
 * @param embeddingDimensions the length of its vectors, as the table's column is declared
 * @param embeddingBatchSize places embedded per job
 * @param embeddingPendingTimeoutMinutes how late a place job's answer may be before it is sent again
 * @param queryPendingTimeoutSeconds how late a query's answer may be before it is sent again
 * @param maxResults the most results a search may ask for
 */
@ConfigurationProperties(prefix = "app.destination.search")
public record SearchProperties(
        String embeddingModel,
        int embeddingDimensions,
        int embeddingBatchSize,
        int embeddingPendingTimeoutMinutes,
        int queryPendingTimeoutSeconds,
        int maxResults
) {
}
