package com.triphuddle.api.destination.messaging;

import com.triphuddle.api.destination.social.QuestionService;
import com.triphuddle.api.destination.semantic.SemanticSearchService;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedEvent;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedContract;
import com.triphuddle.amqp.ai.AiTextCompletedContract;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.api.destination.content.ContentReplyService;
import com.triphuddle.api.destination.trip.ItineraryService;
import com.triphuddle.api.destination.visa.VisaGuidanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Takes the openai worker's answers to api-destination's jobs: place content, itineraries, visa guidance, answers to
 * questions, and embeddings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReplyListener {

    private final ContentReplyService contentReplyService;
    private final ItineraryService itineraryService;
    private final VisaGuidanceService visaGuidanceService;
    private final QuestionService questionService;
    private final SemanticSearchService semanticSearchService;

    /**
     * Apply a text answer.
     *
     * @param reply the answer
     */
    @RabbitListener(queues = AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onTextCompleted(AiTextCompletedEvent reply) {
        log.info("Received AI text answer [jobId={}, task={}, status={}]", reply.jobId(), reply.task(),
                reply.status());
        // Each kind of job is told apart by its task: itineraries, visa guidance, questions, or place content.
        if (reply.task() != null && reply.task().startsWith("itinerary")) {
            itineraryService.onAnswer(reply);
        } else if ("visa-guidance".equals(reply.task())) {
            visaGuidanceService.onAnswer(reply);
        } else if ("place-question".equals(reply.task())) {
            questionService.onAnswer(reply);
        } else {
            contentReplyService.onCompleted(reply);
        }
    }

    /**
     * Apply embeddings of places or of a search query.
     *
     * @param reply the answer
     */
    @RabbitListener(queues = AiEmbeddingCompletedContract.AI_EMBEDDING_COMPLETED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onEmbeddingCompleted(AiEmbeddingCompletedEvent reply) {
        log.info("Received AI embeddings [jobId={}, task={}, status={}]", reply.jobId(), reply.task(),
                reply.status());
        semanticSearchService.onEmbeddings(reply);
    }
}
