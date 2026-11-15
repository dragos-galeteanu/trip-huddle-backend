package com.triphuddle.api.user.dto;

import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent.ResidencePermit;

import java.util.List;

/**
 * The caller's nationalities and residence permits.
 *
 * @param nationalities the countries whose passports they hold
 * @param residencePermits where they may live besides
 */
public record TravelDocumentsResponse(List<String> nationalities, List<ResidencePermit> residencePermits) {
}
