package com.triphuddle.common.config;

import java.util.UUID;

/**
 * STOMP destinations. Every event goes to the topic of each user concerned, never to a shared one.
 */
public final class StompDestinations {

    public static final String APPLICATION_PREFIX = "/app";

    public static final String BROKER_PREFIX = "/topic";

    // Dots, not slashes, after /topic/: RabbitMQ's STOMP plugin turns the rest into a topic routing key.
    public static final String USER_TOPIC_PREFIX = BROKER_PREFIX + "/user.";

    private StompDestinations() {}

    /**
     * The topic a user subscribes to for all their events.
     *
     * @param userId the ID of the user
     * @return the destination of the user's topic
     */
    public static String userTopic(UUID userId) {
        return USER_TOPIC_PREFIX + userId;
    }
}
