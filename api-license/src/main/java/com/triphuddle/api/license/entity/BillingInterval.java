package com.triphuddle.api.license.entity;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * How long one billing period of a license lasts.
 */
public enum BillingInterval {
    MONTH,
    YEAR;

    /**
     * Work out when a number of periods starting at a moment end, by the UTC calendar.
     *
     * @param start when the first period starts
     * @param periods how many periods
     * @return when the last of them ends
     */
    public Instant addTo(Instant start, int periods) {
        // By the calendar rather than a fixed length, so a monthly period started on the 31st ends on the last day.
        return switch (this) {
            case MONTH -> start.atZone(ZoneOffset.UTC).plusMonths(periods).toInstant();
            case YEAR -> start.atZone(ZoneOffset.UTC).plusYears(periods).toInstant();
        };
    }
}
