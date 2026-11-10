package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a language is deleted.
 *
 * @param languageId the ID of the language
 * @param code the language code
 */
public record LanguageDeletedEvent(UUID languageId, String code) {
}
