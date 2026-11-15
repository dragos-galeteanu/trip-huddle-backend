package com.triphuddle.api.destination.content;

import com.triphuddle.api.destination.entity.ContentSection;

import java.time.Duration;

/**
 * How a section is generated: its AI task, JSON schema, whether it searches the web, and how long it stays fresh.
 *
 * @param section the section
 * @param task the AI task name, which picks the model in the openai worker
 * @param schemaName the JSON schema's name
 * @param jsonSchema the strict JSON schema of its content
 * @param isWebSearch whether the model searches the web, returning its sources
 * @param freshFor how long a version is served before it is refreshed
 * @param instructions what to write
 */
public record SectionSpec(
        ContentSection section,
        String task,
        String schemaName,
        String jsonSchema,
        boolean isWebSearch,
        Duration freshFor,
        String instructions
) {
}
