package com.triphuddle.api.destination.service;

import com.triphuddle.amqp.license.LicenseEntitlementsChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedEvent;
import com.triphuddle.api.destination.entity.CountryCopy;
import com.triphuddle.api.destination.entity.GroupMember;
import com.triphuddle.api.destination.entity.LicenseAccess;
import com.triphuddle.api.destination.repository.CountryCopyRepository;
import com.triphuddle.api.destination.repository.GroupMemberRepository;
import com.triphuddle.api.destination.repository.LicenseAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

/**
 * Keeps the local copies of other services' data in step with their events: countries, license subscriptions and
 * group memberships. Each copy copes with repeated and out-of-order events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CopyService {

    private final CountryCopyRepository countryCopyRepository;
    private final LicenseAccessRepository licenseAccessRepository;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * Record a country that was created.
     *
     * @param countryId the ID of the country
     * @param alpha2 its ISO 3166 alpha-2 code
     */
    public void onCountryCreated(UUID countryId, String alpha2) {
        CountryCopy country = countryCopyRepository.findByCountryId(countryId).orElseGet(CountryCopy::new);
        country.setCountryId(countryId);
        country.setAlpha2(alpha2.toUpperCase());
        countryCopyRepository.save(country);
        log.info("Country recorded [countryId={}, alpha2={}]", countryId, alpha2);
    }

    /**
     * Mark a country disabled or deleted; its places stay, but no new ones can be added under it.
     *
     * @param countryId the ID of the country
     * @param alpha2 its ISO 3166 alpha-2 code
     * @param isDeleted true if deleted, false if disabled
     */
    public void onCountryRemoved(UUID countryId, String alpha2, boolean isDeleted) {
        CountryCopy country = countryCopyRepository.findByCountryId(countryId).orElseGet(() -> {
            CountryCopy created = new CountryCopy();
            created.setCountryId(countryId);
            created.setAlpha2(alpha2.toUpperCase());
            return created;
        });
        Instant now = Instant.now();
        if (isDeleted && country.getDeletedAt() == null) {
            country.setDeletedAt(now);
        } else if (!isDeleted && country.getDisabledAt() == null) {
            country.setDisabledAt(now);
        }
        countryCopyRepository.save(country);
        log.info("Country removed [countryId={}, isDeleted={}]", countryId, isDeleted);
    }

    /**
     * Record a subscription that started, with its entitlements; one that already ended stays ended.
     *
     * @param event the activation
     */
    public void onSubscriptionActivated(LicenseSubscriptionActivatedEvent event) {
        LicenseAccess access = licenseAccessRepository.findBySubscriptionId(event.subscriptionId())
                .orElseGet(LicenseAccess::new);
        if (access.getEndedAt() != null) {
            log.info("Activation of an ended subscription skipped [subscriptionId={}]", event.subscriptionId());
            return;
        }
        access.setSubscriptionId(event.subscriptionId());
        access.setLicenseId(event.licenseId());
        access.setLicenseCode(event.licenseCode());
        access.setSubscriberType(event.subscriberType());
        access.setSubscriberId(event.subscriberId());
        access.setEntitlements(event.entitlements() == null ? new HashMap<>() : new HashMap<>(event.entitlements()));
        access.setCurrentPeriodEnd(event.currentPeriodEnd());
        licenseAccessRepository.save(access);
        log.info("License access recorded [subscriptionId={}, subscriberType={}, subscriberId={}]",
                event.subscriptionId(), event.subscriberType(), event.subscriberId());
    }

    /**
     * Move a subscription's access on to its new period end; an older end is ignored.
     *
     * @param event the renewal
     */
    public void onSubscriptionRenewed(LicenseSubscriptionRenewedEvent event) {
        licenseAccessRepository.findBySubscriptionId(event.subscriptionId())
                .filter(access -> access.getEndedAt() == null
                        && event.currentPeriodEnd().isAfter(access.getCurrentPeriodEnd()))
                .ifPresent(access -> {
                    access.setCurrentPeriodEnd(event.currentPeriodEnd());
                    log.info("License access renewed [subscriptionId={}, periodEnd={}]", event.subscriptionId(),
                            event.currentPeriodEnd());
                });
    }

    /**
     * End a subscription's access; recorded even if its activation hasn't arrived, so it can't open it later.
     *
     * @param event the end
     */
    public void onSubscriptionEnded(LicenseSubscriptionEndedEvent event) {
        LicenseAccess access = licenseAccessRepository.findBySubscriptionId(event.subscriptionId()).orElseGet(() -> {
            LicenseAccess created = new LicenseAccess();
            created.setSubscriptionId(event.subscriptionId());
            created.setLicenseId(event.licenseId());
            created.setSubscriberType(event.subscriberType());
            created.setSubscriberId(event.subscriberId());
            created.setCurrentPeriodEnd(event.endedAt());
            return created;
        });
        access.setEndedAt(event.endedAt());
        licenseAccessRepository.save(access);
        log.info("License access ended [subscriptionId={}, status={}]", event.subscriptionId(), event.status());
    }

    /**
     * Give every subscription to a license its new entitlements.
     *
     * @param event the change
     */
    public void onEntitlementsChanged(LicenseEntitlementsChangedEvent event) {
        var subscriptions = licenseAccessRepository.findByLicenseId(event.licenseId());
        subscriptions.forEach(access -> access.setEntitlements(new HashMap<>(event.entitlements())));
        log.info("License entitlements changed [licenseId={}, subscriptions={}]", event.licenseId(),
                subscriptions.size());
    }

    /**
     * Record a membership that was created or changed.
     *
     * @param groupMembershipId the ID of the membership
     * @param groupId its group
     * @param userId its user
     * @param role ADMIN or MEMBER
     */
    public void onMembershipChanged(UUID groupMembershipId, UUID groupId, UUID userId, String role) {
        // Upserted by membership ID, so a repeated or out-of-order event does no harm.
        GroupMember member = groupMemberRepository.findByGroupMembershipId(groupMembershipId)
                .orElseGet(GroupMember::new);
        member.setGroupMembershipId(groupMembershipId);
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role);
        groupMemberRepository.save(member);
        log.info("Group member recorded [groupMembershipId={}, groupId={}, role={}]", groupMembershipId, groupId,
                role);
    }

    /**
     * Forget a membership that was disabled or deleted.
     *
     * @param groupMembershipId the ID of the membership
     */
    public void onMembershipRemoved(UUID groupMembershipId) {
        if (groupMemberRepository.deleteByGroupMembershipId(groupMembershipId) > 0) {
            log.info("Group member removed [groupMembershipId={}]", groupMembershipId);
        }
    }

    /**
     * Forget the members of a group that was disabled or deleted.
     *
     * @param groupId the ID of the group
     */
    public void onGroupRemoved(UUID groupId) {
        int removed = groupMemberRepository.deleteByGroupId(groupId);
        log.info("Group removed [groupId={}, membersRemoved={}]", groupId, removed);
    }
}
