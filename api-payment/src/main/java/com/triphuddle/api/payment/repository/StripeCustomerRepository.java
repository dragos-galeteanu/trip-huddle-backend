package com.triphuddle.api.payment.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.StripeCustomer;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link StripeCustomer}.
 */
@Repository
public interface StripeCustomerRepository extends BaseJpaRepository<StripeCustomer, UUID> {

    /**
     * Find the Stripe customer of a user or group.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @return the customer, once one was created
     */
    Optional<StripeCustomer> findBySubscriberTypeAndSubscriberId(LicenseTarget subscriberType, UUID subscriberId);

    /**
     * Store a subscriber's customer unless one is stored already, as two checkouts may create it at once.
     *
     * @return 1 if stored, 0 if one was there
     */
    @Modifying
    @Query(value = """
            insert into payment_customers (id, created_at, updated_at, subscriber_type, subscriber_id,
                stripe_customer_id)
            values (gen_random_uuid(), now(), now(), :subscriberType, :subscriberId, :stripeCustomerId)
            on conflict (subscriber_type, subscriber_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("subscriberType") String subscriberType, @Param("subscriberId") UUID subscriberId,
                       @Param("stripeCustomerId") String stripeCustomerId);
}
