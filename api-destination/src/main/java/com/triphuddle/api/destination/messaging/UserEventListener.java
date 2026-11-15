package com.triphuddle.api.destination.messaging;

import com.triphuddle.api.destination.social.ReviewService;
import com.triphuddle.api.destination.social.QuestionService;
import com.triphuddle.api.destination.social.PlaceMarkService;
import com.triphuddle.amqp.user.UserDeletedContract;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedContract;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent;
import com.triphuddle.api.destination.visa.TravellerDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Keeps travellers' nationalities and residence permits in step with api-user, for visa guidance, and forgets
 * what a deleted user left here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final TravellerDocumentService documentService;
    private final PlaceMarkService markService;
    private final ReviewService reviewService;
    private final QuestionService questionService;

    /**
     * Copy a user's latest documents.
     *
     * @param event the documents
     */
    @RabbitListener(queues = UserTravelDocumentsUpdatedContract.USER_TRAVEL_DOCUMENTS_UPDATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onTravelDocumentsUpdated(UserTravelDocumentsUpdatedEvent event) {
        log.info("Received user travel documents updated [userId={}]", event.userId());
        documentService.onUpdated(event);
    }

    /**
     * Forget a deleted user's documents, favourites, visited places, reviews and questions.
     *
     * @param event the deleted user
     */
    @RabbitListener(queues = UserDeletedContract.USER_DELETED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Received user deleted [userId={}]", event.userId());
        documentService.onUserDeleted(event.userId());
        markService.deleteForUser(event.userId());
        reviewService.deleteForUser(event.userId());
        questionService.deleteForUser(event.userId());
    }
}
