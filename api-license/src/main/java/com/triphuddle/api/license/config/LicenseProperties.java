package com.triphuddle.api.license.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.license}.
 *
 * @param currency the ISO 4217 code every license is priced in
 * @param pendingExpiryHours how long a subscription may wait for payment before it is ended
 * @param renewalIntervalMs how often the renewal job runs
 * @param pendingTimeoutIntervalMs how often the job that ends stale pending subscriptions runs
 * @param renewalReminderDays how many days before an auto-renewal its reminder goes out
 * @param renewalReminderIntervalMs how often the renewal reminder job runs
 */
@ConfigurationProperties(prefix = "app.license")
public record LicenseProperties(String currency, int pendingExpiryHours, long renewalIntervalMs,
                                long pendingTimeoutIntervalMs, int renewalReminderDays,
                                long renewalReminderIntervalMs) {
}
