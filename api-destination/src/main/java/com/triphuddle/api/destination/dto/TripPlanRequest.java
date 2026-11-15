package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.BudgetLevel;
import com.triphuddle.api.destination.entity.TripPace;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A trip plan to create, or its new details; the group is set at creation only.
 */
@Data
@NoArgsConstructor
public class TripPlanRequest {

    /**
     * The group the plan is for, whose members share it, or null for the caller alone; only read at creation.
     */
    private UUID groupId;

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @Min(1)
    @Max(50)
    private int adults = 1;

    @Min(0)
    @Max(50)
    private int children;

    @NotNull
    private BudgetLevel budgetLevel = BudgetLevel.MODERATE;

    @NotNull
    private TripPace pace = TripPace.BALANCED;

    /**
     * What the travellers like, e.g. food, history, nature, nightlife, beaches, art.
     */
    @Size(max = 12)
    private List<@Pattern(regexp = "^[a-z][a-z-]{1,31}$") String> interests = new ArrayList<>();

    /**
     * The places the trip goes to, in order.
     */
    @NotEmpty
    private List<UUID> placeIds = new ArrayList<>();

    @Size(max = 30)
    private List<UUID> mustSeePlaceIds = new ArrayList<>();

    @Size(max = 2000)
    private String notes;

    /**
     * The language itineraries are written in, ISO 639-1; missing means English.
     */
    @Pattern(regexp = "^[a-zA-Z]{2}$")
    private String language;
}
