package com.triphuddle.openai.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedEvent;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedEvent;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.openai.client.EmbeddingResult;
import com.triphuddle.openai.client.OpenAiApiClient;
import com.triphuddle.openai.client.TextGeneration;
import com.triphuddle.openai.client.TextResult;
import com.triphuddle.openai.config.OpenAiProperties;
import com.triphuddle.openai.config.OpenAiWorkerProperties;
import com.triphuddle.openai.exception.OpenAiCallException;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs one AI job: checks the request, picks the model, calls OpenAI with retries, and checks the answer.
 * It never throws for a job that can't be done; it answers FAILED with the reason.
 */
@Slf4j
public class AiJobRunner {

    // OpenAI's rule for schema names.
    private static final Pattern SCHEMA_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    // An answer cut short at the token limit is tried again with more room, up to this many times the request's.
    private static final int MAX_TOKEN_GROWTH = 4;

    private final OpenAiApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;
    private final OpenAiWorkerProperties workerProperties;
    private final Sleeper sleeper;
    private final Clock clock;

    /**
     * Pauses between attempts; a parameter so tests need not wait.
     */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public AiJobRunner(OpenAiApiClient apiClient, ObjectMapper objectMapper, OpenAiProperties properties,
                       OpenAiWorkerProperties workerProperties, Sleeper sleeper, Clock clock) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.workerProperties = workerProperties;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    /**
     * Run a text job.
     *
     * @param request the job
     * @return the answer, COMPLETED or FAILED
     */
    public AiTextCompletedEvent runText(AiTextRequestedEvent request) {
        if (!isEnabled()) {
            return failedText(request, null, 0, 0, 0, "AI is disabled: set integration.openai.enabled and api-key");
        }
        if (request.userPrompt() == null || request.userPrompt().isBlank()) {
            return failedText(request, null, 0, 0, 0, "A user prompt is required");
        }
        JsonNode schema = null;
        if (request.jsonSchema() != null && !request.jsonSchema().isBlank()) {
            if (request.schemaName() == null || !SCHEMA_NAME.matcher(request.schemaName()).matches()) {
                return failedText(request, null, 0, 0, 0, "A schema needs a name of 1 to 64 letters, digits, _ or -");
            }
            try {
                schema = objectMapper.readTree(request.jsonSchema());
            } catch (JsonProcessingException e) {
                return failedText(request, null, 0, 0, 0,
                        "The JSON schema is not valid JSON: " + e.getOriginalMessage());
            }
        }

        String model = modelFor(request.task(), request.model());
        int requestedTokens = request.maxOutputTokens() == null || request.maxOutputTokens() <= 0
                ? workerProperties.maxOutputTokens() : request.maxOutputTokens();
        TextGeneration generation = new TextGeneration(model, request.systemPrompt(), request.userPrompt(),
                request.schemaName(), schema, request.isWebSearch(), requestedTokens);

        long inputTokens = 0;
        long outputTokens = 0;
        String lastError = null;
        for (int attempt = 1; attempt <= workerProperties.maxAttempts(); attempt++) {
            TextResult result;
            try {
                result = apiClient.generate(generation);
            } catch (OpenAiCallException e) {
                lastError = e.getMessage();
                if (!e.isRetryable() || !pauseBeforeRetry(attempt, e.getRetryAfter(), request)) {
                    return failedText(request, model, inputTokens, outputTokens, attempt, lastError);
                }
                continue;
            }
            inputTokens += result.inputTokens();
            outputTokens += result.outputTokens();
            if (result.refusal() != null) {
                return failedText(request, result.model(), inputTokens, outputTokens, attempt,
                        "The model refused: " + result.refusal());
            }
            if (result.isIncomplete() || result.output() == null) {
                lastError = "The answer was cut short at %d tokens".formatted(generation.maxOutputTokens());
                int grown = Math.min(generation.maxOutputTokens() * 2, requestedTokens * MAX_TOKEN_GROWTH);
                generation = generation.withMaxOutputTokens(grown);
            } else if (schema != null && !isJson(result.output())) {
                lastError = "The answer is not the JSON the schema asks for";
            } else {
                log.info("AI text job done [jobId={}, task={}, model={}, attempts={}, inputTokens={}, outputTokens={}]",
                        request.jobId(), request.task(), result.model(), attempt, inputTokens, outputTokens);
                return new AiTextCompletedEvent(request.jobId(), request.task(), AiJobStatus.COMPLETED,
                        result.output(), result.citations(), result.model(), inputTokens, outputTokens, attempt, null,
                        clock.instant());
            }
            if (attempt < workerProperties.maxAttempts() && !pauseBeforeRetry(attempt, null, request)) {
                return failedText(request, model, inputTokens, outputTokens, attempt, lastError);
            }
        }
        return failedText(request, model, inputTokens, outputTokens, workerProperties.maxAttempts(), lastError);
    }

    /**
     * Run an embedding job.
     *
     * @param request the job
     * @return the answer, COMPLETED or FAILED
     */
    public AiEmbeddingCompletedEvent runEmbedding(AiEmbeddingRequestedEvent request) {
        if (!isEnabled()) {
            return failedEmbedding(request, null, "AI is disabled: set integration.openai.enabled and api-key");
        }
        List<String> inputs = request.inputs();
        if (inputs == null || inputs.isEmpty() || inputs.size() > AiEmbeddingRequestedEvent.MAX_INPUTS
                || inputs.stream().anyMatch(input -> input == null || input.isBlank())) {
            return failedEmbedding(request, null, "Give 1 to %d texts, none of them blank"
                    .formatted(AiEmbeddingRequestedEvent.MAX_INPUTS));
        }
        String model = request.model() == null || request.model().isBlank()
                ? workerProperties.embeddingModel() : request.model();
        String lastError = null;
        for (int attempt = 1; attempt <= workerProperties.maxAttempts(); attempt++) {
            try {
                EmbeddingResult result = apiClient.embed(model, inputs);
                log.info("AI embedding job done [jobId={}, task={}, model={}, inputs={}, attempts={}]",
                        request.jobId(), request.task(), result.model(), inputs.size(), attempt);
                return new AiEmbeddingCompletedEvent(request.jobId(), request.task(), AiJobStatus.COMPLETED,
                        result.vectors(), result.model(), result.inputTokens(), null, clock.instant());
            } catch (OpenAiCallException e) {
                lastError = e.getMessage();
                if (!e.isRetryable() || !pauseBeforeRetry(attempt, e.getRetryAfter(), request)) {
                    break;
                }
            }
        }
        return failedEmbedding(request, model, lastError);
    }

    /**
     * The model for a task: the request's own, else the task's configured one, else the default.
     *
     * @param task the task
     * @param requested the model the request asked for, or null
     * @return the model
     */
    String modelFor(String task, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        String configured = task == null ? null : workerProperties.taskModels().get(task);
        return configured == null || configured.isBlank() ? properties.model() : configured;
    }

    private boolean isEnabled() {
        return properties.enabled() && properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    private boolean isJson(String output) {
        try {
            JsonNode node = objectMapper.readTree(output);
            return node != null && (node.isObject() || node.isArray());
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    // False when no attempt is left, or the thread was interrupted.
    private boolean pauseBeforeRetry(int attempt, Duration retryAfter, Object request) {
        if (attempt >= workerProperties.maxAttempts()) {
            return false;
        }
        long backoff = Math.min(workerProperties.retryBaseMs() << (attempt - 1), workerProperties.maxRetryMs());
        long wait = retryAfter == null ? backoff : Math.min(Math.max(retryAfter.toMillis(), backoff),
                workerProperties.maxRetryMs());
        log.warn("AI job attempt failed, retrying [attempt={}, waitMs={}, request={}]", attempt, wait,
                request.getClass().getSimpleName());
        try {
            sleeper.sleep(Duration.ofMillis(wait));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private AiTextCompletedEvent failedText(AiTextRequestedEvent request, String model, long inputTokens,
                                            long outputTokens, int attempts, String error) {
        log.warn("AI text job failed [jobId={}, task={}, attempts={}, error={}]", request.jobId(), request.task(),
                attempts, error);
        return new AiTextCompletedEvent(request.jobId(), request.task(), AiJobStatus.FAILED, null, List.of(), model,
                inputTokens, outputTokens, attempts, error, clock.instant());
    }

    private AiEmbeddingCompletedEvent failedEmbedding(AiEmbeddingRequestedEvent request, String model, String error) {
        log.warn("AI embedding job failed [jobId={}, task={}, error={}]", request.jobId(), request.task(), error);
        return new AiEmbeddingCompletedEvent(request.jobId(), request.task(), AiJobStatus.FAILED, List.of(), model, 0,
                error, clock.instant());
    }
}
