package com.triphuddle.platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CountryResponse(
        UUID id,
        String alpha2,
        String alpha3,
        String numericCode,
        String name,
        String callingCode,
        BigDecimal standardVatRate,
        boolean isEuVatMember,
        boolean isEuMember,
        boolean isEeaMember,
        boolean isSchengenMember,
        UUID defaultCurrencyId,
        UUID defaultLanguageId,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
