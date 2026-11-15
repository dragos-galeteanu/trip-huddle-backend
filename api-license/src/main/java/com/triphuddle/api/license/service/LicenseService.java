package com.triphuddle.api.license.service;

import com.triphuddle.api.license.dto.CreateLicenseRequest;
import com.triphuddle.api.license.dto.EntitlementRequest;
import com.triphuddle.api.license.dto.LicenseFilter;
import com.triphuddle.api.license.dto.LicenseResponse;
import com.triphuddle.api.license.dto.UpdateLicenseRequest;
import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseEntitlement;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.mapper.LicenseMapper;
import com.triphuddle.api.license.messaging.LicenseEventPublisher;
import com.triphuddle.api.license.repository.LicenseRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages licenses through their lifecycle, DRAFT to PUBLISHED to ARCHIVED, and their entitlements.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final LicenseMapper licenseMapper;
    private final PricingService pricingService;
    private final LicenseEventPublisher eventPublisher;

    /**
     * Create a license as a draft, with its entitlements.
     *
     * @param request the license
     * @return the created license
     */
    public LicenseResponse create(CreateLicenseRequest request) {
        requireUniqueCode(request.getCode(), null);

        License license = new License();
        license.setCode(request.getCode());
        license.setTarget(request.getTarget());
        applyDetails(license, request.getName(), request.getDescription(), request.getPriceMinor(),
                request.getBillingInterval());
        replaceEntitlements(license, request.getEntitlements());

        License saved = licenseRepository.save(license);
        log.info("License created [id={}, code={}]", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    /**
     * Get a license; anyone but staff only finds published ones.
     *
     * @param id the ID of the license
     * @param isStaff whether the caller is Admin, Support or Inspector
     * @return the license
     */
    @Transactional(readOnly = true)
    public LicenseResponse getById(UUID id, boolean isStaff) {
        License license = getEntityById(id);
        if (!isStaff && license.getStatus() != LicenseStatus.PUBLISHED) {
            // Not found rather than forbidden, so drafts don't leak through their IDs.
            throw notFound(id);
        }
        return toResponse(license);
    }

    /**
     * Get licenses, cheapest first; anyone but staff only sees published ones.
     *
     * @param filter the page and the filters
     * @param isStaff whether the caller is Admin, Support or Inspector
     * @return a page of licenses
     */
    @Transactional(readOnly = true)
    public Paginated<LicenseResponse> getAll(LicenseFilter filter, boolean isStaff) {
        LicenseStatus status = isStaff ? filter.getStatus() : LicenseStatus.PUBLISHED;
        Page<License> page = licenseRepository.findCatalog(status, filter.getTarget(), filter.toPageable());
        Map<UUID, Promotion> promotions = pricingService.findAutomatic(
                page.getContent().stream().map(License::getId).toList(), Instant.now());
        return Paginated.of(page, licenses -> licenseMapper.toResponseList(licenses, promotions));
    }

    /**
     * Update a license's details. Subscribers keep the price they subscribed at.
     *
     * @param id the ID of the license
     * @param request the new details
     * @return the updated license
     */
    public LicenseResponse update(UUID id, UpdateLicenseRequest request) {
        License license = getEntityById(id);
        boolean isIdentityChanged = !license.getCode().equals(request.getCode())
                || license.getTarget() != request.getTarget();
        // Code and target are what subscriptions and other services rely on once it is out.
        if (isIdentityChanged && license.getStatus() != LicenseStatus.DRAFT) {
            throw new IllegalStateException("Code and target of license with id=%s can only change while it is a draft"
                    .formatted(id));
        }
        requireUniqueCode(request.getCode(), id);

        license.setCode(request.getCode());
        license.setTarget(request.getTarget());
        applyDetails(license, request.getName(), request.getDescription(), request.getPriceMinor(),
                request.getBillingInterval());

        License saved = licenseRepository.save(license);
        if (saved.getStatus() != LicenseStatus.DRAFT) {
            eventPublisher.catalogChanged(saved);
        }
        log.info("License updated [id={}]", saved.getId());
        return toResponse(saved);
    }

    /**
     * Replace every entitlement of a license; they apply to its subscribers at once.
     *
     * @param id the ID of the license
     * @param entitlements the new entitlements
     * @return the updated license
     */
    public LicenseResponse replaceEntitlements(UUID id, List<EntitlementRequest> entitlements) {
        License license = getEntityById(id);
        if (license.getStatus() == LicenseStatus.ARCHIVED) {
            throw new IllegalStateException("License with id=%s is archived".formatted(id));
        }
        replaceEntitlements(license, entitlements);

        License saved = licenseRepository.save(license);
        eventPublisher.entitlementsChanged(saved);
        log.info("License entitlements replaced [id={}, count={}]", saved.getId(), saved.getEntitlements().size());
        return toResponse(saved);
    }

    /**
     * Publish a draft or archived license, so it takes new subscriptions.
     *
     * @param id the ID of the license
     * @return the published license
     */
    public LicenseResponse publish(UUID id) {
        License license = getEntityById(id);
        if (license.getStatus() == LicenseStatus.PUBLISHED) {
            throw new IllegalStateException("License with id=%s is already published".formatted(id));
        }
        license.setStatus(LicenseStatus.PUBLISHED);
        license.setPublishedAt(Instant.now());
        license.setArchivedAt(null);

        License saved = licenseRepository.save(license);
        eventPublisher.catalogChanged(saved);
        log.info("License published [id={}]", saved.getId());
        return toResponse(saved);
    }

    /**
     * Archive a published license: it takes no new subscriptions, but existing ones keep running.
     *
     * @param id the ID of the license
     * @return the archived license
     */
    public LicenseResponse archive(UUID id) {
        License license = getEntityById(id);
        if (license.getStatus() != LicenseStatus.PUBLISHED) {
            throw new IllegalStateException("Only a published license can be archived, license with id=%s is %s"
                    .formatted(id, license.getStatus()));
        }
        license.setStatus(LicenseStatus.ARCHIVED);
        license.setArchivedAt(Instant.now());

        License saved = licenseRepository.save(license);
        eventPublisher.catalogChanged(saved);
        log.info("License archived [id={}]", saved.getId());
        return toResponse(saved);
    }

    /**
     * Delete a draft license; a published one is archived instead.
     *
     * @param id the ID of the license
     * @return the deleted license
     */
    public LicenseResponse delete(UUID id) {
        License license = getEntityById(id);
        if (license.getStatus() != LicenseStatus.DRAFT) {
            throw new IllegalStateException("Only a draft can be deleted, archive license with id=%s instead"
                    .formatted(id));
        }
        license.setDeletedAt(Instant.now());

        License saved = licenseRepository.save(license);
        log.info("License deleted [id={}]", saved.getId());
        return toResponse(saved);
    }

    /**
     * Get a license that is not deleted.
     *
     * @param id the ID of the license
     * @return the license
     */
    @Transactional(readOnly = true)
    public License getEntityById(UUID id) {
        return licenseRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    private void applyDetails(License license, String name, String description, long priceMinor,
                              BillingInterval billingInterval) {
        license.setName(name.strip());
        license.setDescription(description == null || description.isBlank() ? null : description.strip());
        license.setPriceMinor(priceMinor);
        license.setBillingInterval(billingInterval);
    }

    private void replaceEntitlements(License license, List<EntitlementRequest> requested) {
        Set<String> keys = new HashSet<>();
        for (EntitlementRequest entitlement : requested) {
            if (!keys.add(entitlement.getKey())) {
                throw new IllegalStateException("Entitlement key=%s is given more than once"
                        .formatted(entitlement.getKey()));
            }
        }

        // Update rows in place by key: Hibernate inserts before it deletes, so removing a key and
        // adding it back as a new row would break the unique (license, key) constraint.
        Map<String, LicenseEntitlement> existing = license.getEntitlements().stream()
                .collect(Collectors.toMap(LicenseEntitlement::getEntitlementKey, Function.identity()));
        license.getEntitlements().removeIf(entitlement -> !keys.contains(entitlement.getEntitlementKey()));
        for (EntitlementRequest entitlement : requested) {
            String value = entitlement.getValue() == null || entitlement.getValue().isBlank()
                    ? null : entitlement.getValue().strip();
            LicenseEntitlement row = existing.get(entitlement.getKey());
            if (row == null) {
                row = new LicenseEntitlement();
                row.setLicense(license);
                row.setEntitlementKey(entitlement.getKey());
                license.getEntitlements().add(row);
            }
            row.setEntitlementValue(value);
        }
    }

    private void requireUniqueCode(String code, UUID id) {
        boolean isTaken = id == null
                ? licenseRepository.existsByCodeAndDeletedAtIsNull(code)
                : licenseRepository.existsByCodeAndIdNotAndDeletedAtIsNull(code, id);
        if (isTaken) {
            throw new IllegalStateException("License with code=%s already exists".formatted(code));
        }
    }

    private LicenseResponse toResponse(License license) {
        Promotion promotion = pricingService.findAutomatic(List.of(license.getId()), Instant.now()).get(license.getId());
        return licenseMapper.toResponse(license, promotion);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("License with id=%s not found".formatted(id));
    }
}
