package com.triphuddle.api.payment.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.group.GroupMembershipDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipUpdatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.user.UserContactUpdatedEvent;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.entity.GroupAdmin;
import com.triphuddle.api.payment.entity.StripeCustomer;
import com.triphuddle.api.payment.entity.UserContact;
import com.triphuddle.api.payment.job.CatalogSyncJob;
import com.triphuddle.api.payment.job.ReconciliationJob;
import com.triphuddle.api.payment.job.StripeEventJob;
import com.triphuddle.api.payment.repository.GroupAdminRepository;
import com.triphuddle.api.payment.repository.StripeCustomerRepository;
import com.triphuddle.api.payment.repository.UserContactRepository;
import com.triphuddle.api.payment.service.BillingSubscriptionService;
import com.triphuddle.api.payment.service.CatalogService;
import com.triphuddle.api.payment.service.CatalogSyncException;
import com.triphuddle.api.payment.service.CatalogSyncService;
import com.triphuddle.api.payment.service.CustomerService;
import com.triphuddle.api.payment.service.GroupAdminService;
import com.triphuddle.api.payment.service.PaymentFixtures;
import com.triphuddle.api.payment.service.ReconciliationException;
import com.triphuddle.api.payment.service.ReconciliationService;
import com.triphuddle.api.payment.service.StripeBillingService;
import com.triphuddle.api.payment.service.StripeEventProcessingException;
import com.triphuddle.api.payment.service.StripeEventProcessor;
import com.triphuddle.api.payment.service.UserContactService;
import com.triphuddle.stripe.customer.StripeCustomerClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class PaymentListenersAndJobsTest {

    private final UUID group = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID membership = UUID.randomUUID();

    @Test
    void groupEventsKeepTheCopyOfGroupAdmins() {
        GroupAdminRepository repository = mock(GroupAdminRepository.class);
        GroupAdminService service = new GroupAdminService(repository);
        GroupEventListener listener = new GroupEventListener(service);
        given(repository.findByGroupMembershipId(membership)).willReturn(Optional.empty(),
                Optional.of(new GroupAdmin()));
        given(repository.deleteByGroupMembershipId(membership)).willReturn(1, 0);

        listener.onMembershipCreated(new GroupMembershipCreatedEvent(membership, group, user, "ADMIN"));
        listener.onMembershipUpdated(new GroupMembershipUpdatedEvent(membership, group, user, "ADMIN"));
        listener.onMembershipUpdated(new GroupMembershipUpdatedEvent(membership, group, user, "MEMBER"));
        listener.onMembershipDisabled(new GroupMembershipDisabledEvent(membership, group, user, "ADMIN"));
        listener.onMembershipDeleted(new GroupMembershipDeletedEvent(membership, group, user, "ADMIN"));
        listener.onGroupDisabled(new GroupDisabledEvent(group, "Trip"));
        listener.onGroupDeleted(new GroupDeletedEvent(group, "Trip"));

        then(repository).should(times(2)).save(any());
        then(repository).should(times(3)).deleteByGroupMembershipId(membership);
        then(repository).should(times(2)).deleteByGroupId(group);
    }

    @Nested
    class Users {

        private final UserContactRepository contacts = mock(UserContactRepository.class);
        private final StripeCustomerRepository stripeCustomers = mock(StripeCustomerRepository.class);
        private final StripeCustomerClient customerClient = mock(StripeCustomerClient.class);
        private final CustomerService customers = new CustomerService(stripeCustomers, customerClient);
        private final StripeBillingService billing = mock(StripeBillingService.class);
        private final UserEventListener listener = new UserEventListener(new UserContactService(contacts, customers),
                billing);

        @Test
        void aNewerContactIsKeptAndPassedToTheStripeCustomerButAnOlderOneIsIgnored() {
            UserContact stored = PaymentFixtures.contact(user, true);
            stored.setContactChangedAt(Instant.parse("2026-06-01T00:00:00Z"));
            given(contacts.findByUserId(user)).willReturn(Optional.of(stored));
            StripeCustomer customer = new StripeCustomer();
            customer.setStripeCustomerId("cus_1");
            given(stripeCustomers.findBySubscriberTypeAndSubscriberId(LicenseTarget.USER, user))
                    .willReturn(Optional.of(customer));

            listener.onContactUpdated(new UserContactUpdatedEvent(user, "old@example.com", "A", "B", true,
                    Instant.parse("2026-05-01T00:00:00Z")));
            listener.onContactUpdated(new UserContactUpdatedEvent(user, "new@example.com", "Ioana", null, true,
                    Instant.parse("2026-07-01T00:00:00Z")));

            assertThat(stored.getEmail()).isEqualTo("new@example.com");
            then(customerClient).should(times(1)).updateContact("cus_1", "new@example.com", "Ioana");
        }

        @Test
        void aDisabledOrDeletedUserIsMarkedOnceAndTheirPaidSubscriptionsAreToldToStopRenewing() {
            UserContact stored = PaymentFixtures.contact(user, true);
            given(contacts.findByUserId(user)).willReturn(Optional.of(stored));

            listener.onUserDisabled(new UserDisabledEvent(user));
            listener.onUserDisabled(new UserDisabledEvent(user));
            listener.onUserDeleted(new UserDeletedEvent(user));
            listener.onUserDeleted(new UserDeletedEvent(user));

            assertThat(stored.getDisabledAt()).isNotNull();
            assertThat(stored.getDeletedAt()).isNotNull();
            then(contacts).should(times(2)).save(stored);
            // Every event is passed on; onUserRemoved itself skips subscriptions already stopping.
            then(billing).should(times(4)).onUserRemoved(user);
        }

        @Test
        void aStripeCustomerIsCreatedOnceAndAGroupsCarriesNoPersonalName() {
            UserContact payer = PaymentFixtures.contact(user, true);
            given(customerClient.create(anyString(), any(), any(), anyString())).willReturn("cus_new");
            given(stripeCustomers.findBySubscriberTypeAndSubscriberId(any(), any())).willReturn(Optional.empty());
            given(stripeCustomers.insertIfAbsent(anyString(), any(), anyString())).willReturn(1, 0);

            assertThat(customers.ensureCustomer(LicenseTarget.USER, user, payer)).isEqualTo("cus_new");
            assertThat(customers.ensureCustomer(LicenseTarget.GROUP, group, payer)).isEqualTo("cus_new");

            then(customerClient).should().create(eq("payer@example.com"), eq("Ana Pop"), any(),
                    eq("customer-user-" + user));
            then(customerClient).should().create(eq("payer@example.com"), isNull(), any(), eq("customer-group-" + group));
        }

        @Test
        void aContactWithoutEmailOrStripeCustomerUpdatesNothing() {
            UserContact noEmail = PaymentFixtures.contact(user, true);
            noEmail.setEmail(null);
            UserContact noName = PaymentFixtures.contact(user, true);
            noName.setFirstName(null);
            noName.setLastName(null);
            StripeCustomer customer = new StripeCustomer();
            customer.setStripeCustomerId("cus_2");
            given(stripeCustomers.findBySubscriberTypeAndSubscriberId(LicenseTarget.USER, user))
                    .willReturn(Optional.of(customer));

            customers.onUserContactChanged(noEmail);
            customers.onUserContactChanged(noName);

            then(customerClient).should(times(1)).updateContact("cus_2", "payer@example.com", null);
        }
    }

    @Test
    void licenseResumesReachStripeAndEventsAreSentAfterCommit() {
        StripeBillingService billing = mock(StripeBillingService.class);
        LicenseEventListener listener = new LicenseEventListener(mock(CatalogService.class),
                mock(BillingSubscriptionService.class), billing);
        UUID subscription = UUID.randomUUID();
        RabbitMQMessageProducer producer = mock(RabbitMQMessageProducer.class);

        listener.onSubscriptionResumed(new LicenseSubscriptionResumedEvent(subscription, user));
        new PaymentEventPublisher(producer).renewalChanged(subscription, true);

        then(billing).should().onRenewalRequested(subscription, true);
        then(producer).should().publishAfterCommit(any(), eq(""), anyString());
    }

    @Nested
    class Jobs {

        private final PaymentProperties properties = new PaymentProperties(1, 10, 1, null, "s", "c", "u", 1, 24);

        @Test
        void eachJobRecordsAFailureAndGoesOnStopsAtAnUnexpectedErrorAndStopsWhenNothingIsDue() {
            StripeEventProcessor events = mock(StripeEventProcessor.class);
            UUID event = UUID.randomUUID();
            RuntimeException cause = new IllegalStateException("handler failed");
            given(events.processNext(any())).willThrow(new StripeEventProcessingException(event, cause))
                    .willReturn(Optional.of(UUID.randomUUID())).willReturn(Optional.empty());
            new StripeEventJob(events).processDue();
            then(events).should().recordFailure(eq(event), eq(cause), any());
            then(events).should(times(3)).processNext(any());

            CatalogSyncService catalog = mock(CatalogSyncService.class);
            UUID license = UUID.randomUUID();
            given(catalog.syncNext(any())).willThrow(new CatalogSyncException(license, cause))
                    .willThrow(new IllegalStateException("database down"));
            new CatalogSyncJob(catalog).syncDue();
            then(catalog).should().recordFailure(eq(license), eq(cause), any());
            then(catalog).should(times(2)).syncNext(any());

            ReconciliationService reconciliation = mock(ReconciliationService.class);
            given(reconciliation.reconcileNext(any(), any())).willThrow(new ReconciliationException(license, cause))
                    .willReturn(Optional.of(license)).willThrow(new IllegalStateException("x"));
            new ReconciliationJob(reconciliation, properties).reconcileDue();
            then(reconciliation).should().recordFailure(eq(license), eq(cause), any());
            then(reconciliation).should(times(3)).reconcileNext(any(), any());

            StripeEventProcessor stuck = mock(StripeEventProcessor.class);
            given(stuck.processNext(any())).willThrow(new IllegalStateException("database down"));
            new StripeEventJob(stuck).processDue();
            then(stuck).should(never()).recordFailure(any(), any(), any());
        }

        @Test
        void aRunStopsAtItsBoundAndLeavesTheRestToTheNextRun() {
            StripeEventProcessor events = mock(StripeEventProcessor.class);
            given(events.processNext(any())).willAnswer(i -> Optional.of(UUID.randomUUID()));
            new StripeEventJob(events).processDue();
            then(events).should(times(200)).processNext(any());

            CatalogSyncService catalog = mock(CatalogSyncService.class);
            given(catalog.syncNext(any())).willAnswer(i -> Optional.of(UUID.randomUUID()));
            new CatalogSyncJob(catalog).syncDue();
            then(catalog).should(times(50)).syncNext(any());

            ReconciliationService reconciliation = mock(ReconciliationService.class);
            given(reconciliation.reconcileNext(any(), any())).willAnswer(i -> Optional.of(UUID.randomUUID()));
            new ReconciliationJob(reconciliation, properties).reconcileDue();
            then(reconciliation).should(times(100)).reconcileNext(any(), any());

            ReconciliationService idle = mock(ReconciliationService.class);
            given(idle.reconcileNext(any(), any())).willReturn(Optional.empty());
            new ReconciliationJob(idle, properties).reconcileDue();
            then(idle).should(times(1)).reconcileNext(any(), any());
        }

        @Test
        void propertiesFillTheirDefaults() {
            PaymentProperties defaults = new PaymentProperties(1, 10, 1, " ", "s", "c", "u", 1, 0);

            assertThat(defaults.productTaxCode()).isNull();
            assertThat(defaults.reconcileEveryHours()).isEqualTo(24);
            assertThat(new PaymentProperties(1, 10, 1, " txcd_1 ", "s", "c", "u", 1, 6).productTaxCode())
                    .isEqualTo("txcd_1");
        }
    }
}
