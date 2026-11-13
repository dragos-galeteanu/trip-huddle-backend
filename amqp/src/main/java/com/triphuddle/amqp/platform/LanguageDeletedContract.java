package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link LanguageDeletedEvent}.
 */
public class LanguageDeletedContract {
    public static final String LANGUAGE_DELETED_QUEUE_NAME = "platform.language.deleted.queue";
    public static final String LANGUAGE_DELETED_DLQ_NAME = "platform.language.deleted.dlq";
}
