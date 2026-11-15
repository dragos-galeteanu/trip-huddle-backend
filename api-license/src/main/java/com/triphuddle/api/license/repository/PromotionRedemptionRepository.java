package com.triphuddle.api.license.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.entity.PromotionRedemption;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link PromotionRedemption}.
 */
@Repository
public interface PromotionRedemptionRepository extends BaseJpaRepository<PromotionRedemption, UUID> {

    /**
     * Check if a subscriber used a promotion before.
     *
     * @param promotionId the ID of the promotion
     * @param subscriberType whether the subscriber is a user or a group
     * @param subscriberId the ID of the subscriber
     * @return true if one of its subscriptions redeemed the promotion
     */
    boolean existsByPromotionIdAndSubscriberTypeAndSubscriberId(UUID promotionId, LicenseTarget subscriberType,
                                                                 UUID subscriberId);

    /**
     * Find the redemption of a subscription.
     *
     * @param subscriptionId the ID of the subscription
     * @return the redemption, or empty if the subscription used no promotion
     */
    Optional<PromotionRedemption> findBySubscriptionId(UUID subscriptionId);
}
