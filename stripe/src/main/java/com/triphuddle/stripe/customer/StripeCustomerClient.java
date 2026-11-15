package com.triphuddle.stripe.customer;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Creates Stripe Customers and keeps their email and name current.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeCustomerClient {

    private final StripeClient stripeClient;

    /**
     * Create a customer; repeating the call with the same key returns the same customer while Stripe keeps the key.
     *
     * @param email the email address invoices go to
     * @param name the name, or null
     * @param metadata extra key-values to store on it
     * @param idempotencyKey a key unique to the customer being created
     * @return the Stripe customer ID
     */
    public String create(String email, String name, Map<String, String> metadata, String idempotencyKey) {
        CustomerCreateParams.Builder params = CustomerCreateParams.builder().setEmail(email);
        if (name != null) {
            params.setName(name);
        }
        metadata.forEach(params::putMetadata);
        try {
            Customer customer = stripeClient.v1().customers().create(params.build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
            log.info("Stripe customer created [customerId={}]", customer.getId());
            return customer.getId();
        } catch (StripeException e) {
            throw new StripeIntegrationException("Creating Stripe customer failed", e);
        }
    }

    /**
     * Change a customer's email address and name.
     *
     * @param customerId the Stripe customer ID
     * @param email the new email address
     * @param name the new name, or null to leave it
     */
    public void updateContact(String customerId, String email, String name) {
        CustomerUpdateParams.Builder params = CustomerUpdateParams.builder().setEmail(email);
        if (name != null) {
            params.setName(name);
        }
        try {
            stripeClient.v1().customers().update(customerId, params.build());
            log.info("Stripe customer updated [customerId={}]", customerId);
        } catch (StripeException e) {
            throw new StripeIntegrationException("Updating Stripe customer %s failed".formatted(customerId), e);
        }
    }
}
