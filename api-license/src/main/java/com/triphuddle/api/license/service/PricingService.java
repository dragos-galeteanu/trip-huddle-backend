package com.triphuddle.api.license.service;

import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.dto.PriceQuoteResponse;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.LicenseTarget;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.PromotionState;
import com.triphuddle.api.license.mapper.PromotionMapper;
import com.triphuddle.api.license.repository.GroupAdminRepository;
import com.triphuddle.api.license.repository.LicenseRepository;
import com.triphuddle.api.license.repository.PromotionRedemptionRepository;
import com.triphuddle.api.license.repository.PromotionRepository;
import com.triphuddle.api.license.repository.SubscriptionRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out which promotion applies to a license and what it then costs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingService {

    private final LicenseRepository licenseRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GroupAdminRepository groupAdminRepository;
    private final PromotionMapper promotionMapper;
    private final LicenseProperties licenseProperties;

    /**
     * Find the running automatic promotion of each license that has one.
     *
     * @param licenseIds the IDs of the licenses
     * @param now the current moment
     * @return the promotions by license ID
     */
    public Map<UUID, Promotion> findAutomatic(Collection<UUID> licenseIds, Instant now) {
        if (licenseIds.isEmpty()) {
            return Map.of();
        }
        return promotionRepository.findRedeemableAutomatic(licenseIds, now).stream()
                .collect(Collectors.toMap(promotion -> promotion.getLicense().getId(), Function.identity(),
                        // Runs can't overlap, so a second one would be a bug; keep the latest start.
                        (a, b) -> a.getStartsAt().isAfter(b.getStartsAt()) ? a : b));
    }

    /**
     * Find the promotion that applies to a license: the one a valid code unlocks, else the running automatic one.
     * With a subscriber, its per-customer limits are checked too: an automatic promotion it may not use is
     * skipped, and a code it may not use is refused.
     *
     * @param license the license
     * @param code the code the customer entered, or null
     * @param subscriber who would subscribe, or null to skip the per-customer limits
     * @param now the current moment
     * @return the promotion, or empty if none applies
     * @throws IllegalStateException if the code is unknown for the license or can't be redeemed
     */
    public Optional<Promotion> findApplicable(License license, String code, Subscriber subscriber, Instant now) {
        if (code == null || code.isBlank()) {
            return Optional.ofNullable(findAutomatic(List.of(license.getId()), now).get(license.getId()))
                    .filter(promotion -> subscriber == null || ineligibilityOf(promotion, subscriber) == null);
        }

        String normalized = code.strip().toUpperCase(Locale.ROOT);
        Promotion promotion = promotionRepository.findByLicenseIdAndCodeAndDeletedAtIsNull(license.getId(), normalized)
                .orElseThrow(() -> new IllegalStateException(
                        "Promotion code %s is not valid for this license".formatted(normalized)));
        String reason = switch (PromotionState.of(promotion, now)) {
            case SCHEDULED -> "has not started yet";
            case ENDED -> "has ended";
            case EXHAUSTED -> "has been used up";
            case RUNNING -> subscriber == null ? null : ineligibilityOf(promotion, subscriber);
        };
        if (reason != null) {
            throw new IllegalStateException("Promotion code %s %s".formatted(normalized, reason));
        }
        return Optional.of(promotion);
    }

    // Whose per-customer limits a quote checks: the caller for a user license, or for a group license a
    // group the caller administers; nobody's otherwise, so a group's history is not shown to outsiders.
    private Subscriber subscriberOf(License license, UUID userId, UUID groupId) {
        if (userId == null) {
            return null;
        }
        if (license.getTarget() == LicenseTarget.USER) {
            return Subscriber.user(userId);
        }
        return groupId != null && groupAdminRepository.existsByGroupIdAndUserId(groupId, userId)
                ? new Subscriber(LicenseTarget.GROUP, groupId) : null;
    }

    // Why a subscriber may not use a promotion, or null if it may.
    private String ineligibilityOf(Promotion promotion, Subscriber subscriber) {
        if (promotion.isOncePerCustomer() && promotionRedemptionRepository
                .existsByPromotionIdAndSubscriberTypeAndSubscriberId(
                        promotion.getId(), subscriber.type(), subscriber.id())) {
            return "was already used";
        }
        if (promotion.isNewCustomersOnly() && subscriptionRepository
                .existsBySubscriberTypeAndSubscriberIdAndActivatedAtIsNotNull(subscriber.type(), subscriber.id())) {
            return "is for new customers only";
        }
        return null;
    }

    /**
     * Quote one period of a license now; anyone but staff can only quote published licenses.
     *
     * @param licenseId the ID of the license
     * @param code the code the customer entered, or null
     * @param isStaff whether the caller is Admin, Support or Inspector
     * @param userId the signed-in caller, or null
     * @param groupId for a group license, the group the caller would subscribe, or null
     * @return the quote
     */
    public PriceQuoteResponse quote(UUID licenseId, String code, boolean isStaff, UUID userId, UUID groupId) {
        License license = licenseRepository.findByIdAndDeletedAtIsNull(licenseId)
                .filter(found -> isStaff || found.getStatus() == LicenseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("License with id=%s not found".formatted(licenseId)));

        Subscriber subscriber = subscriberOf(license, userId, groupId);
        Optional<Promotion> promotion = findApplicable(license, code, subscriber, Instant.now());
        long priceMinor = promotion.map(found -> PriceCalculator.discount(license.getPriceMinor(), found))
                .orElse(license.getPriceMinor());
        return new PriceQuoteResponse(license.getId(), licenseProperties.currency(), license.getBillingInterval(),
                license.getPriceMinor(), priceMinor, promotion.map(promotionMapper::toSummary).orElse(null));
    }
}
