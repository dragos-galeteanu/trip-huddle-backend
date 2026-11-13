package com.triphuddle.openai.api;

import com.triphuddle.openai.dto.GenerateTextRequest;

/**
 * Generates text from a prompt, hiding which AI provider does the work.
 */
public interface AiTextGenerator {

    /**
     * Generate text for the given request.
     *
     * @param request the prompts and material to generate from
     * @return the generated text
     * @throws com.triphuddle.openai.exception.OpenAiIntegrationException if the text cannot be generated
     */
    String generateText(GenerateTextRequest request);
}
