package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.PlaceKind;
import com.triphuddle.common.filter.PaginationFilter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The page and filters of a place search; every filter is optional and they all apply together.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PlaceSearchFilter extends PaginationFilter {

    /**
     * Words to look for in every name a place is known by, forgiving accents and typos.
     */
    @Size(max = 200)
    private String q;

    @Pattern(regexp = "^[A-Za-z]{2}$")
    private String countryCode;

    private PlaceKind kind;

    /**
     * A category; places in any of its subcategories match too.
     */
    private UUID categoryId;

    private String tag;

    /**
     * A place whose whole subtree is searched, at any depth, without itself.
     */
    private UUID withinId;

    /**
     * A place whose direct children are listed.
     */
    private UUID parentId;

    private Boolean isFeatured;

    /**
     * A point to search around, with {@link #longitude} and {@link #radiusKm}; results are sorted by distance
     * unless {@link #q} is given.
     */
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Positive
    private Double radiusKm;

    /**
     * A map area, all four of west, south, east and north.
     */
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double west;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double south;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double east;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double north;

    /**
     * The group the caller acts for, whose license then applies.
     */
    private UUID groupId;

    /**
     * Staff only: hidden places too.
     */
    private Boolean isIncludingHidden = false;
}
