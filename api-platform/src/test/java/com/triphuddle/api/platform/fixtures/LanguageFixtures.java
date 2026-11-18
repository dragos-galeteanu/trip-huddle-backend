package com.triphuddle.api.platform.fixtures;

import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.entity.Language;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valid languages and requests. Codes start with "zz", which no seeded language uses.
 */
public final class LanguageFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private LanguageFixtures() {
    }

    public static CreateLanguageRequest.CreateLanguageRequestBuilder createRequest() {
        return CreateLanguageRequest.builder()
                .code(code(SEQ.getAndIncrement())).name("Testish").nativeName("Testisch").isRightToLeft(false);
    }

    public static UpdateLanguageRequest.UpdateLanguageRequestBuilder updateRequest(UUID id) {
        return UpdateLanguageRequest.builder().id(id)
                .code(code(SEQ.getAndIncrement())).name("Mirrorish").nativeName("hsirorriM").isRightToLeft(true);
    }

    public static Language entity(String code) {
        Language language = new Language();
        language.setId(UUID.randomUUID());
        language.setCode(code);
        language.setName(code + " language");
        language.setNativeName(code);
        language.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        language.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return language;
    }

    public static Language entity() {
        return entity(code(SEQ.getAndIncrement()));
    }

    private static String code(int n) {
        return "zz" + n;
    }
}
