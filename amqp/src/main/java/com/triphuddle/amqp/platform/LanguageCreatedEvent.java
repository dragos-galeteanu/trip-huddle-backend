package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a language is created.
 *
 * @param languageId the ID of the language
 * @param code the language code
 */
public record LanguageCreatedEvent(UUID languageId, String code) {
}
