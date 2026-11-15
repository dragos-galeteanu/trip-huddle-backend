package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserTravelDocumentsUpdatedEvent}, read by api-destination.
 */
public class UserTravelDocumentsUpdatedContract {
    public static final String USER_TRAVEL_DOCUMENTS_UPDATED_QUEUE_NAME = "user.travel.documents.updated.queue";
    public static final String USER_TRAVEL_DOCUMENTS_UPDATED_DLQ_NAME = "user.travel.documents.updated.dlq";
}
