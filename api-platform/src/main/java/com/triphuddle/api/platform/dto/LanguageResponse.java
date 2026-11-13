package com.triphuddle.api.platform.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing a language.
 *
 * @param id the ID of the language
 * @param code the code of the language
 * @param name the name of the language
 * @param nativeName the name of the language in the language itself
 * @param isRightToLeft whether the language is written right to left
 * @param createdAt when the language was created
 * @param updatedAt when the language was last updated
 * @param disabledAt when the language was disabled, or null if it is enabled
 * @param deletedAt when the language was deleted, or null if it was not
 */
public record LanguageResponse(
        UUID id,
        String code,
        String name,
        String nativeName,
        boolean isRightToLeft,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
