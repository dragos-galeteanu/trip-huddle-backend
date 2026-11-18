package com.triphuddle.amqp;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The few contract types that carry behaviour: billing periods and the email request without attachments.
 */
class ContractHelpersTest {

    @Test
    void aPeriodEndsByTheCalendarSoAMonthFromTheThirtyFirstEndsOnTheMonthsLastDay() {
        Instant start = Instant.parse("2026-01-31T10:00:00Z");

        assertThat(BillingInterval.MONTH.months()).isEqualTo(1);
        assertThat(BillingInterval.YEAR.months()).isEqualTo(12);
        assertThat(BillingInterval.MONTH.addTo(start, 1)).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        assertThat(BillingInterval.MONTH.addTo(start, 3)).isEqualTo(Instant.parse("2026-04-30T10:00:00Z"));
        assertThat(BillingInterval.YEAR.addTo(Instant.parse("2028-02-29T00:00:00Z"), 1))
                .isEqualTo(Instant.parse("2029-02-28T00:00:00Z"));
    }

    @Test
    void anEmailWithoutAttachmentsCarriesNone() {
        SendEmailRequest request = new SendEmailRequest(NotificationEmailType.SUPPORT_TICKET_CREATED, "a@example.com",
                Map.of("firstName", "Ana"));

        assertThat(request.attachments()).isNullOrEmpty();
        assertThat(request.templateData()).containsEntry("firstName", "Ana");
    }
}
