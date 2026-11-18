package com.triphuddle.api.payment.security;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.dto.CheckoutRequest;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.PaymentInvoice;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.api.payment.repository.GroupAdminRepository;
import com.triphuddle.api.payment.repository.PaymentInvoiceRepository;
import com.triphuddle.api.payment.service.PaymentFixtures;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaymentPoliciesTest {

    private final GroupAdminRepository groupAdmins = mock(GroupAdminRepository.class);
    private final UUID user = UUID.randomUUID();
    private final Authentication me = Caller.CUSTOMER.authentication(user);

    @Nested
    class Checkout {

        private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
        private final CheckoutAuthorizationPolicy policy = new CheckoutAuthorizationPolicy(subscriptions, groupAdmins);

        @Test
        void theSubscriberOrTheGroupsAdminChecksOut() {
            BillingSubscription mine = PaymentFixtures.pending(user);
            BillingSubscription ofGroup = PaymentFixtures.pending(UUID.randomUUID());
            ofGroup.setSubscriberType(LicenseTarget.GROUP);
            given(subscriptions.findByLicenseSubscriptionId(mine.getLicenseSubscriptionId())).willReturn(Optional.of(mine));
            given(subscriptions.findByLicenseSubscriptionId(ofGroup.getLicenseSubscriptionId()))
                    .willReturn(Optional.of(ofGroup));
            given(groupAdmins.existsByGroupIdAndUserId(ofGroup.getSubscriberId(), user)).willReturn(true);

            assertThat(policy.canCreate(me, request(mine.getLicenseSubscriptionId()))).isTrue();
            assertThat(policy.canCreate(Caller.CUSTOMER.authentication(), request(mine.getLicenseSubscriptionId())))
                    .isFalse();
            assertThat(policy.canCreate(me, request(ofGroup.getLicenseSubscriptionId()))).isTrue();
            assertThat(policy.canCreate(Caller.CUSTOMER.authentication(), request(ofGroup.getLicenseSubscriptionId())))
                    .isFalse();
        }

        @Test
        void anUnknownSubscriptionIsLeftToTheServiceAndNothingElseIsAllowed() {
            assertThat(policy.canCreate(me, request(UUID.randomUUID()))).isTrue();
            assertThat(policy.canCreate(me, request(null))).isFalse();
            assertThat(policy.canCreate(me, null)).isFalse();
            assertThat(policy.canCreate(Caller.ANONYMOUS.authentication(), request(UUID.randomUUID()))).isFalse();
            assertThat(policy.canGetById(me, null) || policy.canGetAll(me) || policy.canUpdate(me, null)
                    || policy.canDelete(me, null) || policy.canDisable(me)).isFalse();
        }

        private CheckoutRequest request(UUID subscriptionId) {
            CheckoutRequest request = new CheckoutRequest();
            request.setSubscriptionId(subscriptionId);
            return request;
        }
    }

    @Nested
    class Invoices {

        private final PaymentInvoiceRepository invoices = mock(PaymentInvoiceRepository.class);
        private final InvoiceAuthorizationPolicy policy = new InvoiceAuthorizationPolicy(invoices, groupAdmins);

        @Test
        void theSubscriberOrTheGroupsAdminReadsAnInvoice() {
            PaymentInvoice mine = PaymentFixtures.invoice(PaymentFixtures.active(user, "sub"), 999);
            PaymentInvoice ofGroup = PaymentFixtures.invoice(PaymentFixtures.active(UUID.randomUUID(), "sub"), 999);
            ofGroup.setSubscriberType(LicenseTarget.GROUP);
            given(invoices.findByIdAndDeletedAtIsNull(mine.getId())).willReturn(Optional.of(mine));
            given(invoices.findByIdAndDeletedAtIsNull(ofGroup.getId())).willReturn(Optional.of(ofGroup));
            given(groupAdmins.existsByGroupIdAndUserId(ofGroup.getSubscriberId(), user)).willReturn(true);

            assertThat(policy.canGetById(me, mine.getId())).isTrue();
            assertThat(policy.canGetById(me, ofGroup.getId())).isTrue();
            assertThat(policy.canGetById(Caller.CUSTOMER.authentication(), mine.getId())).isFalse();
            assertThat(policy.canGetById(me, UUID.randomUUID())).isFalse();
            assertThat(policy.canGetById(me, null)).isFalse();
            assertThat(policy.canGetForGroup(me, ofGroup.getSubscriberId())).isTrue();
            assertThat(policy.canGetForGroup(me, null)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(Caller.class)
        void staffReadEverythingOnlyAdminAndSupportRefundAndNobodyWrites(Caller caller) {
            Authentication who = caller.authentication();
            boolean isStaff = caller == Caller.ADMIN || caller == Caller.SUPPORT || caller == Caller.INSPECTOR;

            assertThat(policy.canGetAll(who)).isEqualTo(isStaff);
            assertThat(policy.canGetById(who, UUID.randomUUID())).isEqualTo(isStaff);
            assertThat(policy.canRefund(who)).isEqualTo(caller == Caller.ADMIN || caller == Caller.SUPPORT);
            assertThat(policy.canGetMine(who)).isEqualTo(caller != Caller.ANONYMOUS);
            assertThat(policy.canCreate(who, null) || policy.canUpdate(who, null) || policy.canDelete(who, null)
                    || policy.canDisable(who)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void stripeEventsAreForStaffAndOnlyAdminAndSupportReplay(Caller caller) {
        StripeEventAuthorizationPolicy policy = new StripeEventAuthorizationPolicy();
        Authentication who = caller.authentication();
        boolean isStaff = caller == Caller.ADMIN || caller == Caller.SUPPORT || caller == Caller.INSPECTOR;

        assertThat(policy.canGetAll(who)).isEqualTo(isStaff);
        assertThat(policy.canGetById(who, UUID.randomUUID())).isEqualTo(isStaff);
        assertThat(policy.canReplay(who)).isEqualTo(caller == Caller.ADMIN || caller == Caller.SUPPORT);
        assertThat(policy.canCreate(who, null) || policy.canUpdate(who, null) || policy.canDelete(who, null)
                || policy.canDisable(who)).isFalse();
    }
}
