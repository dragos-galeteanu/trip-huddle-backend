package com.triphuddle.api.destination.dto;

import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent.ResidencePermit;

import java.util.List;
import java.util.UUID;

/**
 * One traveller's visa checks for a trip.
 *
 * @param userId the traveller
 * @param nationalities their passports' countries
 * @param residencePermits their residence permits
 * @param isMissingDocuments whether they haven't entered a nationality
 * @param checks one per destination country
 */
public record TravellerVisaResponse(UUID userId, List<String> nationalities, List<ResidencePermit> residencePermits,
                                    boolean isMissingDocuments, List<VisaCheckResponse> checks) {
}
