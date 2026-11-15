package com.triphuddle.api.chat.config;

import com.triphuddle.common.config.StompBrokerProperties;
import com.triphuddle.common.config.StorageBucketProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.chat}: the STOMP broker and the attachments bucket.
 *
 * @param broker how socket traffic is brokered
 * @param attachments where attachments are stored and how long their URLs last
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(StompBrokerProperties broker, StorageBucketProperties attachments) {
}
