package com.triphuddle.openai.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One call to the Responses API.
 *
 * @param model the model
 * @param instructions the system instructions, or null
 * @param input the user's request
 * @param schemaName the name of the JSON schema, or null for plain text
 * @param schema the JSON schema the answer must match in strict mode, or null for plain text
 * @param isWebSearch whether the model may search the web
 * @param maxOutputTokens the most tokens to generate
 */
public record TextGeneration(
        String model,
        String instructions,
        String input,
        String schemaName,
        JsonNode schema,
        boolean isWebSearch,
        int maxOutputTokens
) {

    /**
     * The same call with a different token limit.
     *
     * @param tokens the new limit
     * @return the call
     */
    public TextGeneration withMaxOutputTokens(int tokens) {
        return new TextGeneration(model, instructions, input, schemaName, schema, isWebSearch, tokens);
    }
}
