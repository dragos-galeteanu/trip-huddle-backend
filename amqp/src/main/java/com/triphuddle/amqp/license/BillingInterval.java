package com.triphuddle.amqp.license;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * How long one billing period of a license lasts; shared by the services that bill and the events between them.
 */
public enum BillingInterval {
    MONTH(1),
    YEAR(12);

    private final int months;

    BillingInterval(int months) {
        this.months = months;
    }

    /**
     * How many months one period lasts.
     *
     * @return 1 for MONTH, 12 for YEAR
     */
    public int months() {
        return months;
    }

    /**
     * Work out when a number of periods starting at a moment end, by the UTC calendar.
     *
     * @param start when the first period starts
     * @param periods how many periods
     * @return when the last of them ends
     */
    public Instant addTo(Instant start, int periods) {
        // By the calendar rather than a fixed length, so a monthly period started on the 31st ends on the last day.
        return start.atZone(ZoneOffset.UTC).plusMonths((long) months * periods).toInstant();
    }
}
