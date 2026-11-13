package com.triphuddle.openai.dto;

/**
 * What to generate and the material to base it on.
 *
 * @param systemPrompt the instructions for the model, or null for the default writing assistant
 * @param userPrompt the task to perform; required
 * @param existingText the current text to improve, if any
 * @param context background information for the task, if any
 */
public record GenerateTextRequest(
        String systemPrompt,
        String userPrompt,
        String existingText,
        String context
) {
}
