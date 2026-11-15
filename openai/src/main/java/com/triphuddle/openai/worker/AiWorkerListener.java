package com.triphuddle.openai.worker;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedContract;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedContract;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.Set;

/**
 * Takes AI jobs off their queues and answers each on the queue it names, which must be a known reply queue.
 */
@Slf4j
public class AiWorkerListener {

    private final AiJobRunner jobRunner;
    private final RabbitMQMessageProducer messageProducer;
    private final Set<String> replyQueueNames;

    /**
     * @param jobRunner runs the jobs
     * @param messageProducer sends the answers
     * @param replyQueueNames the queues answers may go to
     */
    public AiWorkerListener(AiJobRunner jobRunner, RabbitMQMessageProducer messageProducer,
                            Set<String> replyQueueNames) {
        this.jobRunner = jobRunner;
        this.messageProducer = messageProducer;
        this.replyQueueNames = Set.copyOf(replyQueueNames);
    }

    /**
     * Run a text job and answer it.
     *
     * @param request the job
     */
    @RabbitListener(queues = AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory",
            concurrency = "${integration.openai.worker.concurrency:4}")
    public void onTextRequested(AiTextRequestedEvent request) {
        log.info("Received AI text job [jobId={}, task={}, replyQueue={}, isWebSearch={}]", request.jobId(),
                request.task(), request.replyQueue(), request.isWebSearch());
        if (isUnknownReplyQueue(request.replyQueue(), request)) {
            return;
        }
        messageProducer.publish(jobRunner.runText(request), "", request.replyQueue());
    }

    /**
     * Run an embedding job and answer it.
     *
     * @param request the job
     */
    @RabbitListener(queues = AiEmbeddingRequestedContract.AI_EMBEDDING_REQUESTED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory",
            concurrency = "${integration.openai.worker.concurrency:4}")
    public void onEmbeddingRequested(AiEmbeddingRequestedEvent request) {
        log.info("Received AI embedding job [jobId={}, task={}, replyQueue={}, inputs={}]", request.jobId(),
                request.task(), request.replyQueue(), request.inputs() == null ? 0 : request.inputs().size());
        if (isUnknownReplyQueue(request.replyQueue(), request)) {
            return;
        }
        messageProducer.publish(jobRunner.runEmbedding(request), "", request.replyQueue());
    }

    // A job naming another queue is dropped unanswered: it can't be told, and answering elsewhere could feed
    // AI output into a queue that expects something else.
    private boolean isUnknownReplyQueue(String replyQueue, Object request) {
        if (replyQueue != null && replyQueueNames.contains(replyQueue)) {
            return false;
        }
        log.error("AI job dropped, its reply queue is not a known one [replyQueue={}, request={}]", replyQueue,
                request);
        return true;
    }
}
