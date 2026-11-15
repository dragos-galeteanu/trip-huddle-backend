package com.triphuddle.api.destination.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for reviews and questions about places, under {@code app.destination.social}.
 *
 * @param reviewAutoHideReports open reports that hide a review until staff look at it
 * @param maxQuestionLength the longest question a user may ask
 * @param questionPendingTimeoutMinutes how late an answer may be before the question is given up on
 */
@ConfigurationProperties(prefix = "app.destination.social")
public record SocialProperties(
        int reviewAutoHideReports,
        int maxQuestionLength,
        int questionPendingTimeoutMinutes
) {
}
