package com.triphuddle.api.license.messaging;

import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.group.GroupMembershipDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipUpdatedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionActivatedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionEndedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionPastDueEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewalChangedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewedEvent;
import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.entity.GroupAdmin;
import com.triphuddle.api.license.job.PendingSubscriptionTimeoutJob;
import com.triphuddle.api.license.job.RenewalReminderJob;
import com.triphuddle.api.license.job.SubscriptionRenewalJob;
import com.triphuddle.api.license.repository.GroupAdminRepository;
import com.triphuddle.api.license.service.GroupAdminService;
import com.triphuddle.api.license.service.SubscriptionService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class LicenseListenersAndJobsTest {

    private final SubscriptionService subscriptions = mock(SubscriptionService.class);

    @Nested
    class PaymentEvents {

        private final PaymentEventListener listener = new PaymentEventListener(subscriptions);
        private final UUID id = UUID.randomUUID();
        private final Instant start = Instant.parse("2026-11-01T00:00:00Z");
        private final Instant end = Instant.parse("2026-12-01T00:00:00Z");

        @Test
        void handEachPaymentEventToTheSubscription() {
            listener.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(id, "sub_1", start, end));
            listener.onSubscriptionRenewed(new PaymentSubscriptionRenewedEvent(id, "sub_1", start, end));
            listener.onSubscriptionPastDue(new PaymentSubscriptionPastDueEvent(id, end));
            listener.onSubscriptionEnded(new PaymentSubscriptionEndedEvent(id, "sub_1", end, "canceled"));
            listener.onRenewalChanged(new PaymentSubscriptionRenewalChangedEvent(id, false));

            then(subscriptions).should().activatePaid(id, "sub_1", start, end);
            then(subscriptions).should().renewPaid(id, "sub_1", start, end);
            then(subscriptions).should().markPastDue(id, end);
            then(subscriptions).should().endPaid(id, end, "canceled");
            then(subscriptions).should().syncRenewal(id, false);
        }
    }

    @Nested
    class GroupEvents {

        private final GroupAdminService admins = mock(GroupAdminService.class);
        private final GroupEventListener listener = new GroupEventListener(admins);
        private final UUID membership = UUID.randomUUID();
        private final UUID group = UUID.randomUUID();
        private final UUID user = UUID.randomUUID();

        @Test
        void keepTheCopyOfGroupAdminsAndStopARemovedGroupsSubscriptions() {
            listener.onMembershipCreated(new GroupMembershipCreatedEvent(membership, group, user, "ADMIN"));
            listener.onMembershipUpdated(new GroupMembershipUpdatedEvent(membership, group, user, "MEMBER"));
            listener.onMembershipDisabled(new GroupMembershipDisabledEvent(membership, group, user, "ADMIN"));
            listener.onMembershipDeleted(new GroupMembershipDeletedEvent(membership, group, user, "ADMIN"));
            listener.onGroupDisabled(new GroupDisabledEvent(group, "Trip"));
            listener.onGroupDeleted(new GroupDeletedEvent(group, "Trip"));

            then(admins).should().onMembershipChanged(membership, group, user, "ADMIN");
            then(admins).should().onMembershipChanged(membership, group, user, "MEMBER");
            then(admins).should(times(2)).onMembershipRemoved(membership);
            then(admins).should(times(2)).onGroupRemoved(group);
        }
    }

    @Nested
    class GroupAdmins {

        private final GroupAdminRepository repository = mock(GroupAdminRepository.class);
        private final GroupAdminService service = new GroupAdminService(repository, subscriptions);
        private final UUID membership = UUID.randomUUID();

        @Test
        void anAdminMembershipIsUpsertedByItsIdSoRepeatsDoNoHarm() {
            GroupAdmin existing = new GroupAdmin();
            given(repository.findByGroupMembershipId(membership)).willReturn(Optional.empty(), Optional.of(existing));
            UUID group = UUID.randomUUID();
            UUID user = UUID.randomUUID();

            service.onMembershipChanged(membership, group, user, "ADMIN");
            service.onMembershipChanged(membership, group, user, "ADMIN");

            ArgumentCaptor<GroupAdmin> saved = ArgumentCaptor.forClass(GroupAdmin.class);
            then(repository).should(times(2)).save(saved.capture());
            assertThat(saved.getAllValues().get(1)).isSameAs(existing);
            assertThat(existing.getUserId()).isEqualTo(user);
        }

        @Test
        void aDemotionRemovesTheAdmin() {
            given(repository.deleteByGroupMembershipId(membership)).willReturn(1, 0);

            service.onMembershipChanged(membership, UUID.randomUUID(), UUID.randomUUID(), "MEMBER");
            service.onMembershipRemoved(membership);

            then(repository).should(times(2)).deleteByGroupMembershipId(membership);
            then(repository).should(never()).save(any());
        }

        @Test
        void aRemovedGroupLosesItsAdminsAndItsSubscriptionsStop() {
            UUID group = UUID.randomUUID();

            service.onGroupRemoved(group);

            then(repository).should().deleteByGroupId(group);
            then(subscriptions).should().stopForRemovedGroup(group);
        }
    }

    @Nested
    class Jobs {

        private final LicenseProperties properties = new LicenseProperties("EUR", 48, 1, 1, 3, 1);

        @Test
        void eachJobWorksInBatchesUntilOneComesBackShort() {
            given(subscriptions.renewOrExpireDue(any(), eq(50))).willReturn(50, 50, 7);
            given(subscriptions.endStalePending(any(), eq(50))).willReturn(3);
            given(subscriptions.remindDue(any(), any(), eq(50))).willReturn(0);

            new SubscriptionRenewalJob(subscriptions).renewOrExpire();
            new PendingSubscriptionTimeoutJob(subscriptions, properties).endStalePending();
            new RenewalReminderJob(subscriptions, properties).remind();

            then(subscriptions).should(times(3)).renewOrExpireDue(any(), eq(50));
            then(subscriptions).should(times(1)).endStalePending(any(), eq(50));
            then(subscriptions).should(times(1)).remindDue(any(), any(), eq(50));
        }

        @Test
        void aRunStopsAfterTwentyFullBatchesAndLeavesTheRestToTheNextRun() {
            given(subscriptions.renewOrExpireDue(any(), eq(50))).willReturn(50);
            given(subscriptions.endStalePending(any(), eq(50))).willReturn(50);
            given(subscriptions.remindDue(any(), any(), eq(50))).willReturn(50);

            new SubscriptionRenewalJob(subscriptions).renewOrExpire();
            new PendingSubscriptionTimeoutJob(subscriptions, properties).endStalePending();
            new RenewalReminderJob(subscriptions, properties).remind();

            then(subscriptions).should(times(20)).renewOrExpireDue(any(), eq(50));
            then(subscriptions).should(times(20)).endStalePending(any(), eq(50));
            then(subscriptions).should(times(20)).remindDue(any(), any(), eq(50));
        }

        @Test
        void aPendingSubscriptionIsStaleAfterTheConfiguredHoursAndRemindersLookTheConfiguredDaysAhead() {
            new PendingSubscriptionTimeoutJob(subscriptions, properties).endStalePending();
            new RenewalReminderJob(subscriptions, properties).remind();

            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
            then(subscriptions).should().endStalePending(cutoff.capture(), anyInt());
            assertThat(cutoff.getValue()).isCloseTo(Instant.now().minus(Duration.ofHours(48)),
                    within(5, java.time.temporal.ChronoUnit.SECONDS));
            ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> until = ArgumentCaptor.forClass(Instant.class);
            then(subscriptions).should().remindDue(now.capture(), until.capture(), anyInt());
            assertThat(Duration.between(now.getValue(), until.getValue())).isEqualTo(Duration.ofDays(3));
        }

        @Test
        void aFailingBatchEndsTheRunAndTheNextRunTriesAgain() {
            given(subscriptions.renewOrExpireDue(any(), anyInt())).willThrow(new IllegalStateException("locked"));
            given(subscriptions.endStalePending(any(), anyInt())).willThrow(new IllegalStateException("locked"));
            given(subscriptions.remindDue(any(), any(), anyInt())).willThrow(new IllegalStateException("locked"));

            new SubscriptionRenewalJob(subscriptions).renewOrExpire();
            new PendingSubscriptionTimeoutJob(subscriptions, properties).endStalePending();
            new RenewalReminderJob(subscriptions, properties).remind();

            then(subscriptions).should(times(1)).renewOrExpireDue(any(), anyInt());
        }
    }
}
