package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.VisaRequirement;

/**
 * What one passport needs for a destination, from the dataset.
 *
 * @param passportCode the passport's country
 * @param requirement what is needed, or null if the dataset has no rule
 * @param allowedDays how long a visa-free stay may last, when known
 */
public record VisaRuleResponse(String passportCode, VisaRequirement requirement, Integer allowedDays) {
}
