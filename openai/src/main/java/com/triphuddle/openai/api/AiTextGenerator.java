package com.triphuddle.openai.api;

import com.triphuddle.openai.dto.GenerateTextRequest;

public interface AiTextGenerator {

    String generateText(GenerateTextRequest request);
}
