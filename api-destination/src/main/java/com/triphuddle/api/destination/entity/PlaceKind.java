package com.triphuddle.api.destination.entity;

/**
 * What a place is in the tree; what it offers is told by its categories, which have no limit.
 */
public enum PlaceKind {
    /** A part of a country: a state, province, county or historical region. */
    REGION,
    /** A city, town or village. */
    CITY,
    /** A part of a city: a neighbourhood or quarter. */
    DISTRICT,
    /** A natural or open area: an island, park, mountain, coast or lake. */
    AREA,
    /** A single place to visit: a landmark, museum, castle, venue or restaurant. */
    POI
}
