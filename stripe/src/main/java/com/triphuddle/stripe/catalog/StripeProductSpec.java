package com.triphuddle.stripe.catalog;

import java.util.Map;

/**
 * What a Stripe Product should look like.
 *
 * @param id our ID for it, used as the Stripe product ID
 * @param name the name shown on checkout and invoices
 * @param description the description, or null for none
 * @param isActive whether it is offered; inactive products keep billing existing subscriptions
 * @param taxCode the Stripe Tax code, or null for the account's default
 * @param metadata extra key-values to store on it
 */
public record StripeProductSpec(
        String id,
        String name,
        String description,
        boolean isActive,
        String taxCode,
        Map<String, String> metadata
) {
}
