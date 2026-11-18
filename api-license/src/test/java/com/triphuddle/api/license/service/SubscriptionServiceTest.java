package com.triphuddle.api.license.service;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.dto.SubscribeRequest;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.api.license.mapper.SubscriptionMapper;
import com.triphuddle.api.license.messaging.LicenseEventPublisher;
import com.triphuddle.api.license.repository.GroupAdminRepository;
import com.triphuddle.api.license.repository.LicenseRepository;
import com.triphuddle.api.license.repository.PromotionRedemptionRepository;
import com.triphuddle.api.license.repository.PromotionRepository;
import com.triphuddle.api.license.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The paths of SubscriptionService that the integration tests can't reach: races and messages about unknown rows.
 */
class SubscriptionServiceTest {

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final LicenseRepository licenses = mock(LicenseRepository.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final PricingService pricing = mock(PricingService.class);
    private final GroupAdminRepository groupAdmins = mock(GroupAdminRepository.class);
    private final LicenseEventPublisher events = mock(LicenseEventPublisher.class);

    private final SubscriptionService service = new SubscriptionService(subscriptions, licenses, promotions,
            mock(PromotionRedemptionRepository.class), pricing, mock(SubscriptionMapper.class), groupAdmins, events);

    @Test
    void aPromotionUsedUpByAnotherSubscriberInTheMeantimeIsRefused() {
        License license = LicenseFixtures.license(LicenseTarget.USER, LicenseStatus.PUBLISHED, 1000);
        Promotion promotion = LicenseFixtures.promotion(license, DiscountType.AMOUNT, 100, 0);
        given(licenses.findByIdAndDeletedAtIsNull(license.getId())).willReturn(Optional.of(license));
        given(pricing.findApplicable(eq(license), eq("LAST"), any(), any(Instant.class)))
                .willReturn(Optional.of(promotion));
        given(subscriptions.saveAndFlush(any(Subscription.class))).willAnswer(call -> call.getArgument(0));
        given(promotions.takeRedemption(promotion.getId())).willReturn(0);

        assertThatThrownBy(() -> service.subscribe(UUID.randomUUID(),
                SubscribeRequest.builder().licenseId(license.getId()).code("LAST").build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("has been used up");
    }

    @Test
    void aGroupIsSubscribedOnlyByItsAdminEvenIfThePolicyWasPassed() {
        License license = LicenseFixtures.license(LicenseTarget.GROUP, LicenseStatus.PUBLISHED, 1000);
        UUID group = UUID.randomUUID();
        given(licenses.findByIdAndDeletedAtIsNull(license.getId())).willReturn(Optional.of(license));
        given(groupAdmins.existsByGroupIdAndUserId(eq(group), any())).willReturn(false);

        assertThatThrownBy(() -> service.subscribe(UUID.randomUUID(),
                SubscribeRequest.builder().licenseId(license.getId()).groupId(group).build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Only an admin of group");
    }

    @Test
    void paymentsForSubscriptionsThatDoNotExistAreLoggedAndDropped() {
        UUID unknown = UUID.randomUUID();
        Instant now = Instant.now();

        service.activatePaid(unknown, "sub_1", now, now.plusSeconds(60));
        service.renewPaid(unknown, "sub_1", now, now.plusSeconds(60));

        verifyNoInteractions(events);
    }
}
