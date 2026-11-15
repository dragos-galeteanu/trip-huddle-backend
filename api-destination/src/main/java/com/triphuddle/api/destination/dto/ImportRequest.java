package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A country to import from Wikidata, and what to include; missing numbers use the configured defaults.
 */
@Data
@NoArgsConstructor
public class ImportRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{2}$")
    private String countryCode;

    /**
     * The smallest population a city needs.
     */
    @Min(500)
    private Long minCityPopulation;

    /**
     * The number of Wikipedia articles a place to visit needs, as a measure of notability.
     */
    @Min(1)
    @Max(200)
    private Integer minPoiSitelinks;

    /**
     * Whether to import places to visit, for the categories mapped to Wikidata classes.
     */
    private Boolean isIncludingPois = true;

    /**
     * Whether to add photos from Wikimedia Commons.
     */
    private Boolean isIncludingPhotos = true;
}
