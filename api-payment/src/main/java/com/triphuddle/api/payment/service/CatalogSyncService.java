package com.triphuddle.api.payment.service;

import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.api.payment.entity.LicensePrice;
import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.api.payment.repository.LicensePriceRepository;
import com.triphuddle.api.payment.repository.LicenseProductRepository;
import com.triphuddle.stripe.catalog.StripeCatalogClient;
import com.triphuddle.stripe.catalog.StripePriceSpec;
import com.triphuddle.stripe.catalog.StripeProductSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pushes licenses to their Stripe Product and current Price, and finds or creates the Price for any amount.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSyncService {

    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 4000;

    private final LicenseProductRepository licenseProductRepository;
    private final LicensePriceRepository licensePriceRepository;
    private final StripeCatalogClient stripeCatalogClient;
    private final PaymentProperties paymentProperties;

    /**
     * Push the most overdue license to Stripe, if any.
     *
     * @param now the current time
     * @return the ID of the license synced, or empty if none was due
     * @throws CatalogSyncException if Stripe refused; the transaction is rolled back
     */
    @Transactional
    public Optional<UUID> syncNext(Instant now) {
        List<LicenseProduct> due = licenseProductRepository.lockDueForSync(now, PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return Optional.empty();
        }
        LicenseProduct product = due.get(0);
        try {
            stripeCatalogClient.upsertProduct(new StripeProductSpec(product.getStripeProductId(), product.getName(),
                    product.getDescription(), product.isPublished(), paymentProperties.productTaxCode(),
                    metadataOf(product)));
            LicensePrice price = ensurePrice(product, product.getPriceMinor());
            stripeCatalogClient.setDefaultPrice(product.getStripeProductId(), price.getStripePriceId());
        } catch (RuntimeException e) {
            throw new CatalogSyncException(product.getLicenseId(), e);
        }
        product.setNextSyncAt(null);
        product.setSyncAttempts(0);
        product.setLastSyncError(null);
        product.setSyncedAt(now);
        log.info("License synced to Stripe [licenseId={}, stripeProductId={}]", product.getLicenseId(),
                product.getStripeProductId());
        return Optional.of(product.getLicenseId());
    }

    /**
     * Find or create the Stripe Price of a license at an amount, in its currency and interval. The caller holds
     * the product's lock, so two transactions never create the same price row.
     *
     * @param product the locked product, already in Stripe
     * @param amountMinor the price per period, in minor units
     * @return the price
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LicensePrice ensurePrice(LicenseProduct product, long amountMinor) {
        String lookupKey = "license_%s_%s_%d_%s".formatted(product.getLicenseId(),
                product.getBillingInterval().name().toLowerCase(Locale.ROOT), amountMinor,
                product.getCurrency().toLowerCase(Locale.ROOT));
        Optional<LicensePrice> existing = licensePriceRepository.findByLookupKey(lookupKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        String stripePriceId = stripeCatalogClient.ensurePrice(new StripePriceSpec(product.getStripeProductId(),
                lookupKey, amountMinor, product.getCurrency(), product.getBillingInterval() == BillingInterval.YEAR,
                metadataOf(product)));
        LicensePrice price = new LicensePrice();
        price.setLicenseId(product.getLicenseId());
        price.setAmountMinor(amountMinor);
        price.setCurrency(product.getCurrency());
        price.setBillingInterval(product.getBillingInterval());
        price.setLookupKey(lookupKey);
        price.setStripePriceId(stripePriceId);
        return licensePriceRepository.save(price);
    }

    /**
     * Record a failed sync and schedule the next try, further apart each time.
     *
     * @param licenseId the ID of the license
     * @param error the failure
     * @param now the current time
     */
    @Transactional
    public void recordFailure(UUID licenseId, Throwable error, Instant now) {
        licenseProductRepository.lockByLicenseId(licenseId).ifPresent(product -> {
            int attempts = product.getSyncAttempts() + 1;
            product.setSyncAttempts(attempts);
            String message = String.valueOf(error);
            product.setLastSyncError(message.length() <= MAX_ERROR_LENGTH ? message
                    : message.substring(0, MAX_ERROR_LENGTH));
            // Never given up on: the catalog must reach Stripe before anyone can pay for the license.
            long factor = 1L << Math.min(attempts - 1, 20);
            Duration delay = FIRST_RETRY_DELAY.multipliedBy(factor);
            product.setNextSyncAt(now.plus(delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay));
            log.warn("License sync to Stripe failed, will retry [licenseId={}, attempts={}, nextSyncAt={}]",
                    licenseId, attempts, product.getNextSyncAt(), error);
        });
    }

    private static Map<String, String> metadataOf(LicenseProduct product) {
        return Map.of("licenseId", product.getLicenseId().toString(), "licenseCode", product.getCode());
    }
}
