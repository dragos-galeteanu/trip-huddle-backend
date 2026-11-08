package com.triphuddle.openai.dto;

public record GenerateTextRequest(
        String systemPrompt,
        String userPrompt,
        String existingText,
        String context
) {
}
