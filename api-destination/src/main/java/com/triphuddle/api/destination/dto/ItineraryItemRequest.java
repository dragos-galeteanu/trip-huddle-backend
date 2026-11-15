package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.TimeOfDay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Something a traveller adds to a day themselves.
 */
@Data
@NoArgsConstructor
public class ItineraryItemRequest {

    @NotNull
    private TimeOfDay timeOfDay;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    /**
     * A place it is about, or null.
     */
    private UUID placeId;

    @Min(5)
    @Max(1440)
    private Integer durationMinutes;

    @Size(max = 1000)
    private String tips;
}
