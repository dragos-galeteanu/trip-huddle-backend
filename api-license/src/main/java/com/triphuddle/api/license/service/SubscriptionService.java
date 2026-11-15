package com.triphuddle.api.license.service;

import com.triphuddle.api.license.dto.GrantSubscriptionRequest;
import com.triphuddle.api.license.dto.SubscribeRequest;
import com.triphuddle.api.license.dto.SubscriptionFilter;
import com.triphuddle.api.license.dto.SubscriptionResponse;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.LicenseTarget;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.PromotionRedemption;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.entity.SubscriptionEndReason;
import com.triphuddle.api.license.entity.SubscriptionStatus;
import com.triphuddle.api.license.mapper.SubscriptionMapper;
import com.triphuddle.api.license.messaging.LicenseEventPublisher;
import com.triphuddle.api.license.repository.GroupAdminRepository;
import com.triphuddle.api.license.repository.LicenseRepository;
import com.triphuddle.api.license.repository.PromotionRedemptionRepository;
import com.triphuddle.api.license.repository.PromotionRepository;
import com.triphuddle.api.license.repository.SubscriptionRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs subscriptions: subscribing, activation, grants, cancel and resume, renewals and pending timeouts.
 * A subscriber has at most one ACTIVE and one PENDING_PAYMENT subscription; activating one ends the other.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SubscriptionService {

    // Guards a renewal catching up on a subscription that was left unrenewed for a long time.
    private static final int MAX_RENEWALS_AT_ONCE = 1000;

    private final SubscriptionRepository subscriptionRepository;
    private final LicenseRepository licenseRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final PricingService pricingService;
    private final SubscriptionMapper subscriptionMapper;
    private final GroupAdminRepository groupAdminRepository;
    private final LicenseEventPublisher eventPublisher;

    /**
     * Subscribe a user, or a group they administer, to a published license. The subscription waits
     * for payment, and replaces the subscriber's active one when it is activated.
     *
     * @param userId the ID of the user
     * @param request the license, the group for a group license, and an optional promotion code
     * @return the pending subscription
     */
    public SubscriptionResponse subscribe(UUID userId, SubscribeRequest request) {
        License license = licenseRepository.findByIdAndDeletedAtIsNull(request.getLicenseId())
                .filter(found -> found.getStatus() != LicenseStatus.DRAFT)
                .orElseThrow(() -> licenseNotFound(request.getLicenseId()));
        if (license.getStatus() != LicenseStatus.PUBLISHED) {
            throw new IllegalStateException("License with id=%s takes no new subscriptions".formatted(license.getId()));
        }

        Subscriber subscriber = subscriberFor(license, userId, request.getGroupId());
        if (subscriber.type() == LicenseTarget.GROUP
                && !groupAdminRepository.existsByGroupIdAndUserId(subscriber.id(), userId)) {
            // The policy checked this too; checked again here as it is what a group subscription rests on.
            throw new IllegalStateException("Only an admin of group with id=%s can subscribe it"
                    .formatted(subscriber.id()));
        }
        if (findInStatus(subscriber, SubscriptionStatus.PENDING_PAYMENT).isPresent()) {
            throw new IllegalStateException("A subscription is already waiting for payment, cancel it first");
        }
        findInStatus(subscriber, SubscriptionStatus.ACTIVE)
                .filter(active -> active.getLicense().getId().equals(license.getId()))
                .ifPresent(active -> {
                    throw new IllegalStateException(active.getCanceledAt() == null
                            ? "Already subscribed to this license"
                            : "Already subscribed to this license, resume it instead");
                });

        Instant now = Instant.now();
        Optional<Promotion> promotion = pricingService.findApplicable(license, request.getCode(), subscriber, now);

        Subscription subscription = new Subscription();
        subscription.setLicense(license);
        subscription.setSubscriberType(subscriber.type());
        subscription.setSubscriberId(subscriber.id());
        subscription.setPurchasedByUserId(userId);
        subscription.setBillingInterval(license.getBillingInterval());
        subscription.setListPriceMinor(license.getPriceMinor());
        subscription.setPriceMinor(license.getPriceMinor());
        promotion.ifPresent(found -> {
            subscription.setPromotion(found);
            subscription.setPriceMinor(PriceCalculator.discount(license.getPriceMinor(), found));
            subscription.setPromotionPeriodsRemaining(found.getDurationPeriods());
        });
        Subscription saved = subscriptionRepository.save(subscription);

        // Taken now rather than at activation, so the price quoted to the customer holds until they pay.
        promotion.ifPresent(found -> redeem(found, saved));
        log.info("Subscription created [id={}, licenseId={}, subscriberType={}, subscriberId={}, userId={}, "
                + "promotionId={}]", saved.getId(), license.getId(), subscriber.type(), subscriber.id(), userId,
                promotion.map(Promotion::getId).orElse(null));
        return subscriptionMapper.toResponse(saved);
    }

    /**
     * Activate a subscription waiting for payment: its first period starts now, and the subscriber's
     * active subscription, if any, ends as switched.
     *
     * @param id the ID of the subscription
     * @param actorId who confirmed it
     * @return the active subscription
     */
    public SubscriptionResponse activate(UUID id, UUID actorId) {
        Subscription subscription = lock(id);
        if (subscription.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Subscription with id=%s is not waiting for payment".formatted(id));
        }

        Instant now = Instant.now();
        Optional<Subscription> replaced = endActive(subscriberOf(subscription), now);
        replaced.ifPresent(old -> old.setSwitchedToSubscriptionId(subscription.getId()));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setActivatedAt(now);
        subscription.setActivatedByUserId(actorId);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(subscription.getBillingInterval().addTo(now, 1));

        Subscription saved = subscriptionRepository.save(subscription);
        replaced.ifPresent(eventPublisher::ended);
        eventPublisher.activated(saved);
        log.info("Subscription activated [id={}, actorId={}]", saved.getId(), actorId);
        return subscriptionMapper.toResponse(saved);
    }

    /**
     * Grant a user or a group a license for free for a number of periods; it does not renew. It
     * replaces the subscriber's active subscription, if any.
     *
     * @param actorId who granted it
     * @param request the user or group, the license and the number of periods
     * @return the active subscription
     */
    public SubscriptionResponse grant(UUID actorId, GrantSubscriptionRequest request) {
        License license = licenseRepository.findByIdAndDeletedAtIsNull(request.getLicenseId())
                .orElseThrow(() -> licenseNotFound(request.getLicenseId()));
        if (license.getStatus() == LicenseStatus.DRAFT) {
            throw new IllegalStateException("License with id=%s is a draft".formatted(license.getId()));
        }
        if ((request.getUserId() == null) == (request.getGroupId() == null)) {
            throw new IllegalStateException("Give either a user ID or a group ID");
        }

        Instant now = Instant.now();
        Subscriber subscriber = subscriberFor(license, request.getUserId(), request.getGroupId());
        Subscription subscription = new Subscription();
        subscription.setLicense(license);
        subscription.setSubscriberType(subscriber.type());
        subscription.setSubscriberId(subscriber.id());
        subscription.setPurchasedByUserId(actorId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingInterval(license.getBillingInterval());
        subscription.setListPriceMinor(license.getPriceMinor());
        subscription.setPriceMinor(0);
        subscription.setAutoRenew(false);
        subscription.setGranted(true);
        subscription.setGrantedByUserId(actorId);
        subscription.setActivatedAt(now);
        subscription.setActivatedByUserId(actorId);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(license.getBillingInterval().addTo(now, request.getPeriods()));

        Optional<Subscription> replaced = endActive(subscriber, now);
        Subscription saved = subscriptionRepository.save(subscription);
        replaced.ifPresent(old -> old.setSwitchedToSubscriptionId(saved.getId()));
        replaced.ifPresent(eventPublisher::ended);
        eventPublisher.activated(saved);
        log.info("Subscription granted [id={}, licenseId={}, subscriberType={}, subscriberId={}, actorId={}]",
                saved.getId(), license.getId(), subscriber.type(), subscriber.id(), actorId);
        return subscriptionMapper.toResponse(saved);
    }

    /**
     * Push the end of an active subscription's current period forward.
     *
     * @param id the ID of the subscription
     * @param days how many days to add
     * @param actorId who extended it
     * @return the extended subscription
     */
    public SubscriptionResponse extend(UUID id, int days, UUID actorId) {
        Subscription subscription = lock(id);
        requireStatus(subscription, SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd().plus(Duration.ofDays(days)));

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription extended [id={}, days={}, actorId={}]", saved.getId(), days, actorId);
        return subscriptionMapper.toResponse(saved);
    }

    /**
     * Cancel a subscription: a pending one ends now, an active one stops renewing and runs to the end
     * of its period.
     *
     * @param id the ID of the subscription
     * @param actorId who canceled it
     * @return the canceled subscription
     */
    public SubscriptionResponse cancel(UUID id, UUID actorId) {
        Subscription subscription = lock(id);
        Instant now = Instant.now();
        switch (subscription.getStatus()) {
            case PENDING_PAYMENT -> endPending(subscription, SubscriptionEndReason.PENDING_CANCELED, now);
            case ACTIVE -> {
                if (subscription.isGranted()) {
                    throw new IllegalStateException("Granted subscription with id=%s does not renew".formatted(id));
                }
                if (subscription.getCanceledAt() != null) {
                    throw new IllegalStateException("Subscription with id=%s is already canceled".formatted(id));
                }
                subscription.setAutoRenew(false);
                subscription.setCanceledAt(now);
            }
            default -> throw new IllegalStateException("Subscription with id=%s is over".formatted(id));
        }

        Subscription saved = subscriptionRepository.save(subscription);
        if (saved.getStatus() == SubscriptionStatus.ENDED) {
            eventPublisher.ended(saved);
        } else {
            eventPublisher.canceled(saved, actorId);
        }
        log.info("Subscription canceled [id={}, status={}, actorId={}]", saved.getId(), saved.getStatus(), actorId);
        return subscriptionMapper.toResponse(saved);
    }

    /**
     * Undo the cancel of an active subscription before its period ends, so it renews again.
     *
     * @param id the ID of the subscription
     * @param actorId who resumed it
     * @return the resumed subscription
     */
    public SubscriptionResponse resume(UUID id, UUID actorId) {
        Subscription subscription = lock(id);
        requireStatus(subscription, SubscriptionStatus.ACTIVE);
        if (subscription.getCanceledAt() == null) {
            throw new IllegalStateException("Subscription with id=%s is not canceled".formatted(id));
        }
        subscription.setAutoRenew(true);
        subscription.setCanceledAt(null);

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription resumed [id={}, actorId={}]", saved.getId(), actorId);
        return subscriptionMapper.toResponse(saved);
    }

    /**
     * Get a subscription.
     *
     * @param id the ID of the subscription
     * @return the subscription
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getById(UUID id) {
        return subscriptionMapper.toResponse(subscriptionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(id)));
    }

    /**
     * Get a user's own subscriptions, newest first.
     *
     * @param userId the ID of the user
     * @param filter the page and the status to keep
     * @return a page of subscriptions
     */
    @Transactional(readOnly = true)
    public Paginated<SubscriptionResponse> getMine(UUID userId, SubscriptionFilter filter) {
        Page<Subscription> page = subscriptionRepository.findForSubscriber(LicenseTarget.USER, userId,
                filter.getStatus(), filter.toPageable());
        return Paginated.of(page, subscriptionMapper::toResponseList);
    }

    /**
     * Get a user's active subscription.
     *
     * @param userId the ID of the user
     * @return the subscription, or empty if the user has none
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getActive(UUID userId) {
        return findInStatus(Subscriber.user(userId), SubscriptionStatus.ACTIVE).map(subscriptionMapper::toResponse);
    }

    /**
     * Get subscriptions for staff, newest first.
     *
     * @param filter the page and the filters
     * @return a page of subscriptions
     */
    @Transactional(readOnly = true)
    public Paginated<SubscriptionResponse> getAll(SubscriptionFilter filter) {
        Page<Subscription> page = subscriptionRepository.findForStaff(filter.getSubscriberId(), filter.getLicenseId(),
                filter.getStatus(), filter.toPageable());
        return Paginated.of(page, subscriptionMapper::toResponseList);
    }

    /**
     * Renew or expire one batch of active subscriptions whose period is over: those set to auto-renew
     * get the next period, the rest expire. Without payments a renewal only moves the period on.
     *
     * @param now the current moment
     * @param batchSize the most subscriptions to handle
     * @return how many subscriptions were handled
     */
    public int renewOrExpireDue(Instant now, int batchSize) {
        List<Subscription> due = subscriptionRepository.lockDueForRenewal(now, PageRequest.of(0, batchSize));
        for (Subscription subscription : due) {
            if (subscription.isAutoRenew() && subscription.getLicense().getDeletedAt() == null) {
                renew(subscription, now);
                eventPublisher.renewed(subscription);
            } else {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                subscription.setEndedAt(subscription.getCurrentPeriodEnd());
                eventPublisher.ended(subscription);
                log.info("Subscription expired [id={}]", subscription.getId());
            }
        }
        subscriptionRepository.saveAll(due);
        return due.size();
    }

    /**
     * End one batch of subscriptions left waiting for payment since before a cutoff.
     *
     * @param cutoff the moment they must have been created before
     * @param batchSize the most subscriptions to handle
     * @return how many subscriptions were ended
     */
    public int endStalePending(Instant cutoff, int batchSize) {
        List<Subscription> stale = subscriptionRepository.lockPendingCreatedBefore(cutoff, PageRequest.of(0, batchSize));
        Instant now = Instant.now();
        for (Subscription subscription : stale) {
            endPending(subscription, SubscriptionEndReason.PENDING_TIMEOUT, now);
            eventPublisher.ended(subscription);
            log.info("Pending subscription timed out [id={}]", subscription.getId());
        }
        subscriptionRepository.saveAll(stale);
        return stale.size();
    }

    /**
     * Remind of one batch of auto-renewals coming up before a moment, once per period end.
     *
     * @param now the current moment
     * @param until the latest renewal to remind of
     * @param batchSize the most subscriptions to handle
     * @return how many reminders were sent
     */
    public int remindDue(Instant now, Instant until, int batchSize) {
        List<Subscription> due = subscriptionRepository.lockDueForReminder(now, until, PageRequest.of(0, batchSize));
        for (Subscription subscription : due) {
            subscription.setRemindedPeriodEnd(subscription.getCurrentPeriodEnd());
            eventPublisher.renewalDue(subscription, nextPriceMinorOf(subscription));
        }
        subscriptionRepository.saveAll(due);
        return due.size();
    }

    /**
     * Stop the subscriptions of a group that was disabled or deleted: an active one stops renewing
     * and runs out, a pending one ends.
     *
     * @param groupId the ID of the group
     */
    public void stopForRemovedGroup(UUID groupId) {
        Instant now = Instant.now();
        List<Subscription> subscriptions = subscriptionRepository
                .findBySubscriberTypeAndSubscriberIdAndStatusInAndDeletedAtIsNull(LicenseTarget.GROUP, groupId,
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING_PAYMENT));
        for (Subscription found : subscriptions) {
            Subscription subscription = lock(found.getId());
            if (subscription.getStatus() == SubscriptionStatus.PENDING_PAYMENT) {
                endPending(subscription, SubscriptionEndReason.PENDING_CANCELED, now);
                eventPublisher.ended(subscription);
            } else if (subscription.getStatus() == SubscriptionStatus.ACTIVE && subscription.isAutoRenew()) {
                subscription.setAutoRenew(false);
                subscription.setCanceledAt(now);
                eventPublisher.canceled(subscription, null);
            } else {
                continue;
            }
            subscriptionRepository.save(subscription);
            log.info("Subscription stopped for removed group [id={}, groupId={}]", subscription.getId(), groupId);
        }
    }

    /**
     * Get a group's subscriptions, newest first.
     *
     * @param groupId the ID of the group
     * @param filter the page and the status to keep
     * @return a page of subscriptions
     */
    @Transactional(readOnly = true)
    public Paginated<SubscriptionResponse> getForGroup(UUID groupId, SubscriptionFilter filter) {
        Page<Subscription> page = subscriptionRepository.findForSubscriber(LicenseTarget.GROUP, groupId,
                filter.getStatus(), filter.toPageable());
        return Paginated.of(page, subscriptionMapper::toResponseList);
    }

    /**
     * Get a group's active subscription.
     *
     * @param groupId the ID of the group
     * @return the subscription, or empty if the group has none
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getActiveForGroup(UUID groupId) {
        return findInStatus(new Subscriber(LicenseTarget.GROUP, groupId), SubscriptionStatus.ACTIVE)
                .map(subscriptionMapper::toResponse);
    }

    // What the next period costs: the list price once the promoted periods run out at this renewal.
    private long nextPriceMinorOf(Subscription subscription) {
        Integer remaining = subscription.getPromotionPeriodsRemaining();
        if (subscription.getPromotion() != null && remaining != null && remaining <= 1) {
            return subscription.getListPriceMinor();
        }
        return subscription.getPriceMinor();
    }

    // Who a license is for: the user for a user license, the given group for a group license.
    private Subscriber subscriberFor(License license, UUID userId, UUID groupId) {
        if (license.getTarget() == LicenseTarget.GROUP) {
            if (groupId == null) {
                throw new IllegalStateException("License with id=%s is for groups, give a group ID"
                        .formatted(license.getId()));
            }
            return new Subscriber(LicenseTarget.GROUP, groupId);
        }
        if (groupId != null) {
            throw new IllegalStateException("License with id=%s is for users, not groups".formatted(license.getId()));
        }
        return Subscriber.user(userId);
    }

    private void renew(Subscription subscription, Instant now) {
        int renewals = 0;
        // Catches up period by period, so the promoted periods are counted down the same way.
        while (!subscription.getCurrentPeriodEnd().isAfter(now) && renewals < MAX_RENEWALS_AT_ONCE) {
            Instant start = subscription.getCurrentPeriodEnd();
            subscription.setCurrentPeriodStart(start);
            subscription.setCurrentPeriodEnd(subscription.getBillingInterval().addTo(start, 1));
            subscription.setRenewalCount(subscription.getRenewalCount() + 1);

            Integer remaining = subscription.getPromotionPeriodsRemaining();
            if (subscription.getPromotion() != null && remaining != null) {
                int left = Math.max(remaining - 1, 0);
                subscription.setPromotionPeriodsRemaining(left);
                if (left == 0) {
                    subscription.setPriceMinor(subscription.getListPriceMinor());
                }
            }
            renewals++;
        }
        log.info("Subscription renewed [id={}, periods={}, periodEnd={}]", subscription.getId(), renewals,
                subscription.getCurrentPeriodEnd());
    }

    // Ends a subscription that never started and gives its promotion back, so it can be used again.
    private void endPending(Subscription subscription, SubscriptionEndReason reason, Instant now) {
        subscription.setStatus(SubscriptionStatus.ENDED);
        subscription.setEndReason(reason);
        subscription.setEndedAt(now);
        subscription.setAutoRenew(false);
        if (subscription.getPromotion() != null) {
            promotionRepository.releaseRedemption(subscription.getPromotion().getId());
            promotionRedemptionRepository.findBySubscriptionId(subscription.getId())
                    .ifPresent(promotionRedemptionRepository::delete);
        }
    }

    // Ends the subscriber's active subscription, if any, as switched; flushed at once, as only one
    // ACTIVE subscription per subscriber may exist when the replacement is written. The caller links
    // the replacement, which the next flush writes.
    private Optional<Subscription> endActive(Subscriber subscriber, Instant now) {
        return findInStatus(subscriber, SubscriptionStatus.ACTIVE)
                .flatMap(active -> subscriptionRepository.lockById(active.getId()))
                .map(active -> {
                    active.setStatus(SubscriptionStatus.ENDED);
                    active.setEndReason(SubscriptionEndReason.SWITCHED);
                    active.setEndedAt(now);
                    active.setAutoRenew(false);
                    Subscription ended = subscriptionRepository.saveAndFlush(active);
                    log.info("Subscription switched [id={}]", ended.getId());
                    return ended;
                });
    }

    private void redeem(Promotion promotion, Subscription subscription) {
        if (promotionRepository.takeRedemption(promotion.getId()) == 0) {
            throw new IllegalStateException("Promotion %s has been used up".formatted(promotion.getName()));
        }
        PromotionRedemption redemption = new PromotionRedemption();
        redemption.setPromotion(promotion);
        redemption.setSubscriberType(subscription.getSubscriberType());
        redemption.setSubscriberId(subscription.getSubscriberId());
        redemption.setSubscription(subscription);
        promotionRedemptionRepository.save(redemption);
    }

    private Optional<Subscription> findInStatus(Subscriber subscriber, SubscriptionStatus status) {
        return subscriptionRepository.findFirstBySubscriberTypeAndSubscriberIdAndStatusAndDeletedAtIsNull(
                subscriber.type(), subscriber.id(), status);
    }

    private Subscriber subscriberOf(Subscription subscription) {
        return new Subscriber(subscription.getSubscriberType(), subscription.getSubscriberId());
    }

    private void requireStatus(Subscription subscription, SubscriptionStatus status) {
        if (subscription.getStatus() != status) {
            throw new IllegalStateException("Subscription with id=%s is %s, not %s"
                    .formatted(subscription.getId(), subscription.getStatus(), status));
        }
    }

    private Subscription lock(UUID id) {
        return subscriptionRepository.lockById(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Subscription with id=%s not found".formatted(id));
    }

    private ResourceNotFoundException licenseNotFound(UUID licenseId) {
        return new ResourceNotFoundException("License with id=%s not found".formatted(licenseId));
    }
}
