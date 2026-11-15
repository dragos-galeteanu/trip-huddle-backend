package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.PlaceKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A place to create, or the new details of one; its names, categories and tags are replaced as given.
 * Country and parent are set at creation and changed by moving it.
 */
@Data
@NoArgsConstructor
public class PlaceRequest {

    /**
     * Its country, ISO 3166 alpha-2; only read at creation.
     */
    @Pattern(regexp = "^[A-Za-z]{2}$")
    private String countryCode;

    /**
     * The place it is in, in the same country, or null at the top; only read at creation.
     */
    private UUID parentId;

    @NotNull
    private PlaceKind kind;

    @NotBlank
    @Size(max = 200)
    private String name;

    /**
     * A slug of lower-case letters, digits and hyphens, or null to make one from the country and name.
     */
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
    @Size(max = 220)
    private String slug;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Pattern(regexp = "^Q[0-9]+$")
    private String wikidataId;

    @Pattern(regexp = "^(node|way|relation)/[0-9]+$")
    private String osmId;

    @PositiveOrZero
    private Long population;

    @Valid
    @Size(max = 100)
    private List<PlaceNameRequest> names = new ArrayList<>();

    @Size(max = 20)
    private List<UUID> categoryIds = new ArrayList<>();

    @Size(max = 30)
    private List<@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") @Size(max = 64) String> tags = new ArrayList<>();
}
