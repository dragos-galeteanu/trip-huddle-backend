package com.triphuddle.stripe.catalog;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.StripeCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.PriceUpdateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.ProductUpdateParams;
import com.stripe.param.common.EmptyParam;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * Creates and updates Stripe Products and Prices; every call is safe to repeat.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeCatalogClient {

    private static final String RESOURCE_MISSING = "resource_missing";
    private static final String RESOURCE_ALREADY_EXISTS = "resource_already_exists";

    private final StripeClient stripeClient;

    /**
     * Create the product with the spec's ID, or bring an existing one in line with the spec.
     *
     * @param spec the product
     */
    public void upsertProduct(StripeProductSpec spec) {
        try {
            update(spec);
        } catch (StripeException e) {
            if (!RESOURCE_MISSING.equals(e.getCode())) {
                throw new StripeIntegrationException("Updating Stripe product %s failed".formatted(spec.id()), e);
            }
            create(spec);
        }
    }

    /**
     * Find the price with the spec's lookup key, or create it; a found price that was deactivated is reactivated.
     *
     * @param spec the price
     * @return the Stripe price ID
     */
    public String ensurePrice(StripePriceSpec spec) {
        try {
            StripeCollection<Price> found = stripeClient.v1().prices().list(PriceListParams.builder()
                    .addLookupKey(spec.lookupKey())
                    .setLimit(1L)
                    .build());
            if (!found.getData().isEmpty()) {
                Price price = found.getData().get(0);
                if (!Boolean.TRUE.equals(price.getActive())) {
                    stripeClient.v1().prices().update(price.getId(),
                            PriceUpdateParams.builder().setActive(true).build());
                }
                return price.getId();
            }

            PriceCreateParams.Builder params = PriceCreateParams.builder()
                    .setProduct(spec.productId())
                    .setUnitAmount(spec.amountMinor())
                    .setCurrency(spec.currency().toLowerCase(Locale.ROOT))
                    .setRecurring(PriceCreateParams.Recurring.builder()
                            .setInterval(spec.isYearly()
                                    ? PriceCreateParams.Recurring.Interval.YEAR
                                    : PriceCreateParams.Recurring.Interval.MONTH)
                            .build())
                    // Prices are what the customer pays; Stripe Tax takes the tax out of them.
                    .setTaxBehavior(PriceCreateParams.TaxBehavior.INCLUSIVE)
                    .setLookupKey(spec.lookupKey());
            spec.metadata().forEach(params::putMetadata);
            // Two instances creating it at once get the same price back, for as long as Stripe keeps the key.
            Price created = stripeClient.v1().prices().create(params.build(),
                    RequestOptions.builder().setIdempotencyKey("price-" + spec.lookupKey()).build());
            log.info("Stripe price created [priceId={}, lookupKey={}]", created.getId(), spec.lookupKey());
            return created.getId();
        } catch (StripeException e) {
            throw new StripeIntegrationException("Ensuring Stripe price %s failed".formatted(spec.lookupKey()), e);
        }
    }

    /**
     * Make a price the one the dashboard shows first for its product.
     *
     * @param productId the Stripe product ID
     * @param priceId the Stripe price ID
     */
    public void setDefaultPrice(String productId, String priceId) {
        try {
            stripeClient.v1().products().update(productId,
                    ProductUpdateParams.builder().setDefaultPrice(priceId).build());
        } catch (StripeException e) {
            throw new StripeIntegrationException("Setting default price of Stripe product %s failed"
                    .formatted(productId), e);
        }
    }

    private void update(StripeProductSpec spec) throws StripeException {
        ProductUpdateParams.Builder params = ProductUpdateParams.builder()
                .setName(spec.name())
                .setActive(spec.isActive());
        if (spec.description() == null) {
            params.setDescription(EmptyParam.EMPTY);
        } else {
            params.setDescription(spec.description());
        }
        if (spec.taxCode() != null) {
            params.setTaxCode(spec.taxCode());
        }
        spec.metadata().forEach(params::putMetadata);
        stripeClient.v1().products().update(spec.id(), params.build());
        log.info("Stripe product updated [productId={}, isActive={}]", spec.id(), spec.isActive());
    }

    private void create(StripeProductSpec spec) {
        ProductCreateParams.Builder params = ProductCreateParams.builder()
                .setId(spec.id())
                .setName(spec.name())
                .setActive(spec.isActive());
        if (spec.description() != null) {
            params.setDescription(spec.description());
        }
        if (spec.taxCode() != null) {
            params.setTaxCode(spec.taxCode());
        }
        spec.metadata().forEach(params::putMetadata);
        try {
            stripeClient.v1().products().create(params.build());
            log.info("Stripe product created [productId={}]", spec.id());
        } catch (StripeException e) {
            if (!RESOURCE_ALREADY_EXISTS.equals(e.getCode())) {
                throw new StripeIntegrationException("Creating Stripe product %s failed".formatted(spec.id()), e);
            }
            // Created by another instance in between; bring it in line instead.
            try {
                update(spec);
            } catch (StripeException retry) {
                throw new StripeIntegrationException("Updating Stripe product %s failed".formatted(spec.id()), retry);
            }
        }
    }
}
