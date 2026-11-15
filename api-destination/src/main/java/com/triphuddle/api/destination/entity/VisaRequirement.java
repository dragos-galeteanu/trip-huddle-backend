package com.triphuddle.api.destination.entity;

/**
 * What a passport holder needs to enter a country for a short stay, from the least to the most effort.
 */
public enum VisaRequirement {
    CITIZEN,
    VISA_FREE,
    VISA_ON_ARRIVAL,
    // An electronic travel authorisation, such as the US ESTA, lighter than an e-visa.
    ETA,
    E_VISA,
    VISA_REQUIRED,
    NO_ADMISSION
}
