package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.api.payment.repository.LicenseProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Keeps the local copy of published licenses, and queues each change for {@link CatalogSyncService} to push to Stripe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogService {

    private final LicenseProductRepository licenseProductRepository;

    /**
     * Apply a license's state, unless a newer one is already stored, and queue it for Stripe.
     *
     * @param event the license
     */
    public void onCatalogChanged(LicenseCatalogChangedEvent event) {
        LicenseProduct product = licenseProductRepository.lockByLicenseId(event.licenseId()).orElseGet(() -> {
            LicenseProduct created = new LicenseProduct();
            created.setLicenseId(event.licenseId());
            created.setStripeProductId(stripeProductIdOf(event));
            return created;
        });
        if (product.getCatalogChangedAt() != null && !event.changedAt().isAfter(product.getCatalogChangedAt())) {
            log.info("Stale license catalog change skipped [licenseId={}, changedAt={}]", event.licenseId(),
                    event.changedAt());
            return;
        }
        product.setCode(event.code());
        product.setName(event.name());
        product.setDescription(event.description());
        product.setTarget(event.target());
        product.setLicenseStatus(event.status());
        product.setPriceMinor(event.priceMinor());
        product.setCurrency(event.currency());
        product.setBillingInterval(event.billingInterval());
        product.setCatalogChangedAt(event.changedAt());
        product.setNextSyncAt(Instant.now());
        product.setSyncAttempts(0);
        licenseProductRepository.save(product);
        log.info("License catalog change recorded [licenseId={}, status={}]", event.licenseId(), event.status());
    }

    // Our own product ID in Stripe, so creating it again finds the one that exists.
    private static String stripeProductIdOf(LicenseCatalogChangedEvent event) {
        return "license_" + event.licenseId();
    }
}
