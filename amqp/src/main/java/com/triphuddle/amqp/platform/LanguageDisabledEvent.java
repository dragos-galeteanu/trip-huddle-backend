package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a language is disabled.
 *
 * @param languageId the ID of the language
 * @param code the language code
 */
public record LanguageDisabledEvent(UUID languageId, String code) {
}
