package com.triphuddle.api.platform.dto;

import com.triphuddle.api.platform.entity.SymbolPosition;

import java.time.Instant;
import java.util.UUID;

public record CurrencyResponse(
        UUID id,
        String code,
        String name,
        String symbol,
        SymbolPosition symbolPosition,
        int minorUnits,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
