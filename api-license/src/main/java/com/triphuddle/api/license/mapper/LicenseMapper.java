package com.triphuddle.api.license.mapper;

import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.dto.EntitlementResponse;
import com.triphuddle.api.license.dto.LicenseResponse;
import com.triphuddle.api.license.dto.PromotionSummary;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.service.PriceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link License} to its response, with the currency it is priced in and its running automatic promotion.
 */
@Component
@RequiredArgsConstructor
public class LicenseMapper {

    private final LicenseProperties licenseProperties;
    private final PromotionMapper promotionMapper;

    /**
     * Convert a license to its response.
     *
     * @param license the license, with its entitlements
     * @param promotion the automatic promotion running now, or null if there is none
     * @return the response
     */
    public LicenseResponse toResponse(License license, Promotion promotion) {
        List<EntitlementResponse> entitlements = license.getEntitlements().stream()
                .map(entitlement -> new EntitlementResponse(entitlement.getEntitlementKey(),
                        entitlement.getEntitlementValue()))
                .toList();
        PromotionSummary summary = promotion == null ? null : promotionMapper.toSummary(promotion);
        Long promotedPriceMinor = promotion == null ? null : PriceCalculator.discount(license.getPriceMinor(), promotion);
        return new LicenseResponse(license.getId(), license.getCode(), license.getName(), license.getDescription(),
                license.getTarget(), license.getStatus(), license.getPriceMinor(), licenseProperties.currency(),
                license.getBillingInterval(), entitlements, summary, promotedPriceMinor, license.getPublishedAt(),
                license.getArchivedAt(),
                license.getCreatedAt(), license.getUpdatedAt());
    }

    /**
     * Convert licenses to their responses.
     *
     * @param licenses the licenses
     * @param promotions the running automatic promotion of each license that has one, by license ID
     * @return the responses, in the same order
     */
    public List<LicenseResponse> toResponseList(List<License> licenses, Map<UUID, Promotion> promotions) {
        return licenses.stream().map(license -> toResponse(license, promotions.get(license.getId()))).toList();
    }
}
