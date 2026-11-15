package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.ContentSection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sections to regenerate now, and in which language.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegenerateContentRequest {

    /**
     * The language, ISO 639-1; missing means English.
     */
    private String language;

    /**
     * The sections; empty means all.
     */
    private List<ContentSection> sections = new ArrayList<>();
}
