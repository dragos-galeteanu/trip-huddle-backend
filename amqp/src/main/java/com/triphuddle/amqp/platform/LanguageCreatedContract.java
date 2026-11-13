package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link LanguageCreatedEvent}.
 */
public class LanguageCreatedContract {
    public static final String LANGUAGE_CREATED_QUEUE_NAME = "platform.language.created.queue";
    public static final String LANGUAGE_CREATED_DLQ_NAME = "platform.language.created.dlq";
}
