package com.triphuddle.api.platform.fixtures;

import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.api.platform.entity.SymbolPosition;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valid currencies and requests. Codes are QBA-QZZ, which no seeded currency uses (QAR is the only Q code).
 */
public final class CurrencyFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private CurrencyFixtures() {
    }

    public static CreateCurrencyRequest.CreateCurrencyRequestBuilder createRequest() {
        return CreateCurrencyRequest.builder()
                .code(code(SEQ.getAndIncrement())).name("Test Dollar").symbol("T$")
                .symbolPosition(SymbolPosition.PREFIX).minorUnits(2);
    }

    public static UpdateCurrencyRequest.UpdateCurrencyRequestBuilder updateRequest(UUID id) {
        return UpdateCurrencyRequest.builder().id(id)
                .code(code(SEQ.getAndIncrement())).name("Test Franc").symbol("TF")
                .symbolPosition(SymbolPosition.SUFFIX).minorUnits(0);
    }

    public static Currency entity(String code) {
        Currency currency = new Currency();
        currency.setId(UUID.randomUUID());
        currency.setCode(code);
        currency.setName(code + " currency");
        currency.setSymbol(code);
        currency.setSymbolPosition(SymbolPosition.SUFFIX);
        currency.setMinorUnits(2);
        currency.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        currency.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return currency;
    }

    public static Currency entity() {
        return entity(code(SEQ.getAndIncrement()));
    }

    private static String code(int n) {
        int i = n % (25 * 26);
        return "Q" + (char) ('B' + i / 26) + (char) ('A' + i % 26);
    }
}
