package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.VisaRequirement;

import java.util.List;

/**
 * How one traveller enters one country: the easiest of their passports, and the AI's explanation of it.
 *
 * @param destinationCode the country, ISO 3166-1 alpha-2
 * @param destinationName its English name
 * @param passportCode the passport to travel on, or null without documents
 * @param requirement what that passport needs, or null if unknown
 * @param allowedDays how long a visa-free stay may last, when known
 * @param byNationality what each of the traveller's passports needs
 * @param residencePermitCodes the permits taken into account, valid until the trip ends
 * @param isMissingDocuments whether the traveller hasn't entered a nationality
 * @param guidance the explanation, or null without documents
 */
public record VisaCheckResponse(String destinationCode, String destinationName, String passportCode,
                                VisaRequirement requirement, Integer allowedDays,
                                List<VisaRuleResponse> byNationality, List<String> residencePermitCodes,
                                boolean isMissingDocuments, VisaGuidanceResponse guidance) {
}
