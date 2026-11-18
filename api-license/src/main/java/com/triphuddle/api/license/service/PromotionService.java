package com.triphuddle.api.license.service;

import com.triphuddle.api.license.dto.PromotionRequest;
import com.triphuddle.api.license.dto.PromotionResponse;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.PromotionState;
import com.triphuddle.api.license.mapper.PromotionMapper;
import com.triphuddle.api.license.repository.LicenseRepository;
import com.triphuddle.api.license.repository.PromotionRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages the promotions of licenses: automatic runs can't overlap, and terms freeze once redeemed.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PromotionService {

    private final LicenseRepository licenseRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionMapper promotionMapper;

    /**
     * Create a promotion on a license that is not archived.
     *
     * @param licenseId the ID of the license
     * @param request the promotion
     * @return the created promotion
     */
    public PromotionResponse create(UUID licenseId, PromotionRequest request) {
        License license = lockLicense(licenseId);
        if (license.getStatus() == LicenseStatus.ARCHIVED) {
            throw new IllegalStateException("License with id=%s is archived".formatted(licenseId));
        }

        Promotion promotion = new Promotion();
        promotion.setLicense(license);
        applyTerms(promotion, request);
        promotion.setName(request.getName().strip());
        promotion.setEndsAt(request.getEndsAt());
        promotion.setMaxRedemptions(request.getMaxRedemptions());
        validate(promotion);

        Promotion saved = promotionRepository.saveAndFlush(promotion);
        log.info("Promotion created [id={}, licenseId={}]", saved.getId(), licenseId);
        return promotionMapper.toResponse(saved, Instant.now());
    }

    /**
     * Get the promotions of a license, latest start first.
     *
     * @param licenseId the ID of the license
     * @param filter pagination parameters
     * @return a page of promotions
     */
    @Transactional(readOnly = true)
    public Paginated<PromotionResponse> getAll(UUID licenseId, PaginationFilter filter) {
        requireLicense(licenseId);
        Instant now = Instant.now();
        Page<Promotion> page = promotionRepository.findByLicenseIdAndDeletedAtIsNullOrderByStartsAtDesc(
                licenseId, filter.toPageable());
        return Paginated.of(page, promotions -> promotions.stream()
                .map(promotion -> promotionMapper.toResponse(promotion, now)).toList());
    }

    /**
     * Get a promotion of a license.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @return the promotion
     */
    @Transactional(readOnly = true)
    public PromotionResponse getById(UUID licenseId, UUID id) {
        return promotionMapper.toResponse(getEntity(licenseId, id), Instant.now());
    }

    /**
     * Update a promotion. Once redeemed, only its name, end and redemption limit can change,
     * as subscriptions were promised its terms.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @param request the new promotion
     * @return the updated promotion
     */
    public PromotionResponse update(UUID licenseId, UUID id, PromotionRequest request) {
        lockLicense(licenseId);
        Promotion promotion = getEntity(licenseId, id);

        if (promotion.getRedemptionCount() > 0) {
            Promotion requested = new Promotion();
            applyTerms(requested, request);
            if (!hasSameTerms(promotion, requested)) {
                throw new IllegalStateException(("Promotion with id=%s was redeemed, only its name, end and "
                        + "max redemptions can change").formatted(id));
            }
        } else {
            applyTerms(promotion, request);
        }
        promotion.setName(request.getName().strip());
        promotion.setEndsAt(request.getEndsAt());
        promotion.setMaxRedemptions(request.getMaxRedemptions());
        validate(promotion);

        Promotion saved = promotionRepository.saveAndFlush(promotion);
        log.info("Promotion updated [id={}, licenseId={}]", saved.getId(), licenseId);
        return promotionMapper.toResponse(saved, Instant.now());
    }

    /**
     * End a running promotion now; subscriptions that redeemed it keep their discount.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @return the ended promotion
     */
    public PromotionResponse end(UUID licenseId, UUID id) {
        lockLicense(licenseId);
        Promotion promotion = getEntity(licenseId, id);
        Instant now = Instant.now();
        PromotionState state = PromotionState.of(promotion, now);
        if (state == PromotionState.ENDED) {
            throw new IllegalStateException("Promotion with id=%s has already ended".formatted(id));
        }
        if (state == PromotionState.SCHEDULED) {
            throw new IllegalStateException("Promotion with id=%s has not started, delete it instead".formatted(id));
        }
        promotion.setEndsAt(now);

        Promotion saved = promotionRepository.saveAndFlush(promotion);
        log.info("Promotion ended [id={}, licenseId={}]", saved.getId(), licenseId);
        return promotionMapper.toResponse(saved, now);
    }

    /**
     * Delete a promotion that was never redeemed; a redeemed one is ended instead.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @return the deleted promotion
     */
    public PromotionResponse delete(UUID licenseId, UUID id) {
        lockLicense(licenseId);
        Promotion promotion = getEntity(licenseId, id);
        if (promotion.getRedemptionCount() > 0) {
            throw new IllegalStateException("Promotion with id=%s was redeemed, end it instead".formatted(id));
        }
        promotion.setDeletedAt(Instant.now());

        Promotion saved = promotionRepository.saveAndFlush(promotion);
        log.info("Promotion deleted [id={}, licenseId={}]", saved.getId(), licenseId);
        return promotionMapper.toResponse(saved, Instant.now());
    }

    // The terms a redeemed promotion promised; name, end and max redemptions are not among them.
    private void applyTerms(Promotion promotion, PromotionRequest request) {
        promotion.setCode(request.getCode() == null || request.getCode().isBlank()
                ? null : request.getCode().strip().toUpperCase(Locale.ROOT));
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue());
        promotion.setStartsAt(request.getStartsAt());
        promotion.setDurationPeriods(request.getDurationPeriods());
        promotion.setOncePerCustomer(request.getIsOncePerCustomer());
        promotion.setNewCustomersOnly(request.getIsNewCustomersOnly());
        promotion.setMinPriceMinor(request.getMinPriceMinor());
    }

    private boolean hasSameTerms(Promotion a, Promotion b) {
        return Objects.equals(a.getCode(), b.getCode())
                && a.getDiscountType() == b.getDiscountType()
                && a.getDiscountValue() == b.getDiscountValue()
                && a.getStartsAt().equals(b.getStartsAt())
                && Objects.equals(a.getDurationPeriods(), b.getDurationPeriods())
                && a.isOncePerCustomer() == b.isOncePerCustomer()
                && a.isNewCustomersOnly() == b.isNewCustomersOnly()
                && a.getMinPriceMinor() == b.getMinPriceMinor();
    }

    private void validate(Promotion promotion) {
        if (promotion.getDiscountType() == DiscountType.PERCENTAGE && promotion.getDiscountValue() > 100) {
            throw new IllegalStateException("A percentage discount can be at most 100");
        }
        if (promotion.getEndsAt() != null && !promotion.getEndsAt().isAfter(promotion.getStartsAt())) {
            throw new IllegalStateException("A promotion must end after it starts");
        }
        if (promotion.getMaxRedemptions() != null && promotion.getMaxRedemptions() < promotion.getRedemptionCount()) {
            throw new IllegalStateException("Max redemptions can't be below the %d redemptions so far"
                    .formatted(promotion.getRedemptionCount()));
        }

        UUID licenseId = promotion.getLicense().getId();
        if (promotion.hasCode()) {
            boolean isTaken = promotion.getId() == null
                    ? promotionRepository.existsByLicenseIdAndCodeAndDeletedAtIsNull(licenseId, promotion.getCode())
                    : promotionRepository.existsByLicenseIdAndCodeAndIdNotAndDeletedAtIsNull(
                            licenseId, promotion.getCode(), promotion.getId());
            if (isTaken) {
                throw new IllegalStateException("Promotion code %s is already used on this license"
                        .formatted(promotion.getCode()));
            }
            return;
        }

        // At most one automatic promotion applies at any moment, so their runs can't overlap.
        promotionRepository.findByLicenseIdAndCodeIsNullAndDeletedAtIsNull(licenseId).stream()
                .filter(other -> !other.getId().equals(promotion.getId()))
                .filter(other -> other.overlaps(promotion.getStartsAt(), promotion.getEndsAt()))
                .findFirst()
                .ifPresent(other -> {
                    throw new IllegalStateException("Its run overlaps automatic promotion %s (id=%s)"
                            .formatted(other.getName(), other.getId()));
                });
    }

    // Locked so that two staff members can't add overlapping runs at the same time.
    private License lockLicense(UUID licenseId) {
        return licenseRepository.lockById(licenseId).orElseThrow(() -> licenseNotFound(licenseId));
    }

    private void requireLicense(UUID licenseId) {
        licenseRepository.findByIdAndDeletedAtIsNull(licenseId).orElseThrow(() -> licenseNotFound(licenseId));
    }

    private Promotion getEntity(UUID licenseId, UUID id) {
        return promotionRepository.findByIdAndLicenseIdAndDeletedAtIsNull(id, licenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion with id=%s not found on license with id=%s".formatted(id, licenseId)));
    }

    private ResourceNotFoundException licenseNotFound(UUID licenseId) {
        return new ResourceNotFoundException("License with id=%s not found".formatted(licenseId));
    }
}
