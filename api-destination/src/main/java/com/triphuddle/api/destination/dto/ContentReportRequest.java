package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Why a reader reports a section, in the language they read it in.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ContentReportRequest {

    /**
     * The language of the section read, ISO 639-1; missing means English.
     */
    private String language;

    @NotBlank
    @Size(max = 2000)
    private String reason;
}
