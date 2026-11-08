package com.triphuddle.openai.client;

import com.triphuddle.openai.api.AiTextGenerator;
import com.triphuddle.openai.dto.GenerateTextRequest;
import com.triphuddle.openai.exception.OpenAiIntegrationException;

public class DisabledAiTextGenerator implements AiTextGenerator {

    @Override
    public String generateText(GenerateTextRequest request) {
        throw new OpenAiIntegrationException("OpenAI integration is disabled. Set integration.openai.enabled=true and configure integration.openai.api-key.");
    }
}
