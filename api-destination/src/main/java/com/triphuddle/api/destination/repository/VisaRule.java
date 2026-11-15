package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.VisaRequirement;

/**
 * What one passport needs for one destination.
 *
 * @param passportCode the passport's country, ISO 3166-1 alpha-2
 * @param destinationCode the destination, ISO 3166-1 alpha-2
 * @param requirement what is needed
 * @param allowedDays how long a visa-free stay may last, when known
 */
public record VisaRule(String passportCode, String destinationCode, VisaRequirement requirement,
                       Integer allowedDays) {
}
