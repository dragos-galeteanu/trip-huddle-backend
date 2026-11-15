package com.triphuddle.openai.client;

import java.util.List;

/**
 * What the embeddings endpoint answered.
 *
 * @param vectors one embedding per input, in the inputs' order
 * @param model the model that answered
 * @param inputTokens tokens read
 */
public record EmbeddingResult(List<float[]> vectors, String model, long inputTokens) {
}
