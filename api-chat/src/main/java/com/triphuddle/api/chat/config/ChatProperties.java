package com.triphuddle.api.chat.config;

import com.triphuddle.common.config.StompBrokerProperties;
import com.triphuddle.common.config.StorageBucketProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.chat}: the STOMP broker, the attachments bucket and support tickets.
 *
 * @param broker how socket traffic is brokered
 * @param attachments where attachments are stored and how long their URLs last
 * @param support the limits and timings of support tickets
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(StompBrokerProperties broker, StorageBucketProperties attachments,
                             SupportProperties support) {
}
