package com.triphuddle.api.platform.fixtures;

import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.entity.Country;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valid countries and requests, changed by a test only where it cares. Codes come from ranges ISO leaves to users
 * (alpha-2 QB-QZ and XA-XZ, alpha-3 QBA-QZZ, numeric 900-999), so they never meet the seeded countries;
 * {@link PlatformTestData#deleteTestRows} removes them after each integration test.
 */
public final class CountryFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int ALPHA2_CODES = 25 + 26;

    private CountryFixtures() {
    }

    public static CreateCountryRequest.CreateCountryRequestBuilder createRequest() {
        int n = SEQ.getAndIncrement();
        return CreateCountryRequest.builder()
                .alpha2(alpha2(n)).alpha3(alpha3(n)).numericCode(numeric(n))
                .name("Testland " + n).callingCode("+999")
                .standardVatRate(new BigDecimal("19.00"))
                .isEuVatMember(false).isEuMember(false).isEeaMember(false).isSchengenMember(false);
    }

    public static UpdateCountryRequest.UpdateCountryRequestBuilder updateRequest(UUID id) {
        int n = SEQ.getAndIncrement();
        return UpdateCountryRequest.builder().id(id)
                .alpha2(alpha2(n)).alpha3(alpha3(n)).numericCode(numeric(n))
                .name("Renamed " + n).callingCode("+998")
                .standardVatRate(new BigDecimal("21.50"))
                .isEuVatMember(true).isEuMember(true).isEeaMember(true).isSchengenMember(false);
    }

    /**
     * A saved-looking country: with an ID and audit timestamps.
     */
    public static Country entity() {
        CreateCountryRequest request = createRequest().build();
        Country country = new Country();
        country.setId(UUID.randomUUID());
        country.setAlpha2(request.getAlpha2());
        country.setAlpha3(request.getAlpha3());
        country.setNumericCode(request.getNumericCode());
        country.setName(request.getName());
        country.setCallingCode(request.getCallingCode());
        country.setStandardVatRate(request.getStandardVatRate());
        country.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        country.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return country;
    }

    private static String alpha2(int n) {
        int i = n % ALPHA2_CODES;
        return i < 25 ? "Q" + (char) ('B' + i) : "X" + (char) ('A' + i - 25);
    }

    private static String alpha3(int n) {
        int i = n % (25 * 26);
        return "Q" + (char) ('B' + i / 26) + (char) ('A' + i % 26);
    }

    private static String numeric(int n) {
        return String.valueOf(900 + n % 100);
    }
}
