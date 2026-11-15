package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.ImportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A load of the visa rules.
 *
 * @param id the ID of the import
 * @param status where it is
 * @param sourceUrl the dataset loaded
 * @param requestedByUserId the staff member who asked
 * @param startedAt when it started
 * @param finishedAt when it finished
 * @param ruleCount the rules loaded
 * @param skippedCount rows that weren't rules
 * @param removedCount rules removed as the dataset no longer has them
 * @param error why it failed
 * @param createdAt when it was asked for
 */
public record VisaRuleImportResponse(UUID id, ImportStatus status, String sourceUrl, UUID requestedByUserId,
                                     Instant startedAt, Instant finishedAt, int ruleCount, int skippedCount,
                                     int removedCount, String error, Instant createdAt) {
}
