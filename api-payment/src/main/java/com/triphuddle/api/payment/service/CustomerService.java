package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.StripeCustomer;
import com.triphuddle.api.payment.entity.UserContact;
import com.triphuddle.api.payment.repository.StripeCustomerRepository;
import com.triphuddle.stripe.customer.StripeCustomerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds or creates the Stripe Customer of a user or a group, and keeps a user's customer contact current.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final StripeCustomerRepository stripeCustomerRepository;
    private final StripeCustomerClient stripeCustomerClient;

    /**
     * Get the subscriber's Stripe customer, creating it with the given contact the first time.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @param contact who invoices go to: the user, or for a group the admin paying first
     * @return the Stripe customer ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String ensureCustomer(LicenseTarget subscriberType, UUID subscriberId, UserContact contact) {
        Optional<StripeCustomer> existing =
                stripeCustomerRepository.findBySubscriberTypeAndSubscriberId(subscriberType, subscriberId);
        if (existing.isPresent()) {
            return existing.get().getStripeCustomerId();
        }
        // A group's name is not copied here; the payer can enter a business name at checkout.
        String name = subscriberType == LicenseTarget.USER ? fullNameOf(contact) : null;
        Map<String, String> metadata = Map.of("subscriberType", subscriberType.name(),
                "subscriberId", subscriberId.toString());
        String customerId = stripeCustomerClient.create(contact.getEmail(), name, metadata,
                "customer-%s-%s".formatted(subscriberType.name().toLowerCase(), subscriberId));
        if (stripeCustomerRepository.insertIfAbsent(subscriberType.name(), subscriberId, customerId) == 0) {
            // Another checkout stored one first; with the same idempotency key it is the same customer.
            return stripeCustomerRepository.findBySubscriberTypeAndSubscriberId(subscriberType, subscriberId)
                    .map(StripeCustomer::getStripeCustomerId)
                    .orElse(customerId);
        }
        log.info("Stripe customer recorded [subscriberType={}, subscriberId={}, customerId={}]", subscriberType,
                subscriberId, customerId);
        return customerId;
    }

    /**
     * Copy a user's new contact to their own Stripe customer, if they have one; group customers keep theirs.
     *
     * @param contact the user's contact
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserContactChanged(UserContact contact) {
        if (contact.getEmail() == null) {
            return;
        }
        stripeCustomerRepository.findBySubscriberTypeAndSubscriberId(LicenseTarget.USER, contact.getUserId())
                .ifPresent(customer -> stripeCustomerClient.updateContact(customer.getStripeCustomerId(),
                        contact.getEmail(), fullNameOf(contact)));
    }

    private static String fullNameOf(UserContact contact) {
        String name = ((contact.getFirstName() == null ? "" : contact.getFirstName()) + " "
                + (contact.getLastName() == null ? "" : contact.getLastName())).strip();
        return name.isEmpty() ? null : name;
    }
}
