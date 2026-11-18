package com.triphuddle.api.user.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.chat.SupportTicketCreatedEvent;
import com.triphuddle.amqp.chat.SupportTicketRepliedEvent;
import com.triphuddle.amqp.chat.SupportTicketStatusChangedEvent;
import com.triphuddle.amqp.destination.ItineraryFinishedEvent;
import com.triphuddle.amqp.group.GroupInvitationAcceptedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationContract;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationEvent;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * USR-8: emails go only to verified, active users; and USR-3's matching of invitations to verified users.
 */
class EmailingListenersTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RabbitMQMessageProducer producer = mock(RabbitMQMessageProducer.class);
    private final User verified = UserFixtures.verifiedUser();
    private final User unverified = UserFixtures.user();

    private void givenActive(User user) {
        given(users.findByIdAndDeletedAtIsNullAndDisabledAtIsNull(user.getId())).willReturn(Optional.of(user));
    }

    private SendEmailRequest sentEmail() {
        ArgumentCaptor<SendEmailRequest> email = ArgumentCaptor.forClass(SendEmailRequest.class);
        then(producer).should().publish(email.capture(), eq(""), eq(NotificationEmailContract.EMAIL_QUEUE_NAME));
        return email.getValue();
    }

    @Nested
    class GroupInvitations {

        private final GroupInvitationEventListener listener = new GroupInvitationEventListener(users, producer);
        private final GroupInvitationAcceptedEvent accepted = new GroupInvitationAcceptedEvent(UUID.randomUUID(),
                UUID.randomUUID(), "Friend@Example.com", "MEMBER");

        @Test
        void matchesAVerifiedUserWithTheInvitedAddress() {
            given(users.findByEmailIgnoreCase("Friend@Example.com")).willReturn(Optional.of(verified));

            listener.onGroupInvitationAccepted(accepted);

            then(producer).should().publish(new UserMatchedToGroupInvitationEvent(accepted.groupInvitationId(),
                    verified.getId()), "", UserMatchedToGroupInvitationContract.USER_MATCHED_TO_GROUP_INVITATION_QUEUE_NAME);
        }

        @Test
        void waitsForTheInviteeToSignUpAndVerify() {
            given(users.findByEmailIgnoreCase("Friend@Example.com")).willReturn(Optional.empty(), Optional.of(unverified));

            listener.onGroupInvitationAccepted(accepted);
            listener.onGroupInvitationAccepted(accepted);

            then(producer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Itineraries {

        private final ItineraryEventListener listener = new ItineraryEventListener(users, producer);

        private ItineraryFinishedEvent finished(String status, UUID requestedBy) {
            return new ItineraryFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), "Lisbon", status, List.of(),
                    requestedBy, LocalDate.of(2027, 5, 3), 5, "https://app.example/trips/1");
        }

        @Test
        void emailsTheRequesterWhenTheItineraryIsReady() {
            givenActive(verified);

            listener.onFinished(finished("READY", verified.getId()));

            SendEmailRequest email = sentEmail();
            assertThat(email.type()).isEqualTo(NotificationEmailType.ITINERARY_READY);
            assertThat(email.templateData()).containsEntry("startDate", "3 May 2027").containsEntry("dayCount", 5)
                    .containsEntry("planTitle", "Lisbon");
        }

        @Test
        void sendsNothingForAFailureNoRequesterOrAnUnverifiedRequester() {
            givenActive(unverified);

            listener.onFinished(finished("FAILED", verified.getId()));
            listener.onFinished(finished("READY", null));
            listener.onFinished(finished("READY", unverified.getId()));
            listener.onFinished(finished("READY", UUID.randomUUID()));

            then(producer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class LicenseSubscriptions {

        private final LicenseSubscriptionEventListener listener = new LicenseSubscriptionEventListener(users, producer);
        private final UUID subscriptionId = UUID.randomUUID();

        LicenseSubscriptions() {
            ReflectionTestUtils.setField(listener, "frontendUrl", "https://app.example");
        }

        @Test
        void announcesAnActivationWithItsEndDateAndLink() {
            givenActive(verified);

            listener.onActivated(new LicenseSubscriptionActivatedEvent(subscriptionId, UUID.randomUUID(), "PRO",
                    "Pro", LicenseTarget.GROUP, UUID.randomUUID(), verified.getId(),
                    Instant.parse("2027-01-31T23:00:00Z"), true, false, 999, "EUR", Map.of()));

            SendEmailRequest email = sentEmail();
            assertThat(email.type()).isEqualTo(NotificationEmailType.LICENSE_SUBSCRIPTION_ACTIVATED);
            assertThat(email.templateData())
                    .containsEntry("periodEnd", "31 January 2027")
                    .containsEntry("isGroup", true)
                    .containsEntry("licenseName", "Pro")
                    .containsEntry("subscriptionUrl", "https://app.example/account/subscriptions/" + subscriptionId);
        }

        @ParameterizedTest(name = "{0} minor units of {1} read {2}")
        @CsvSource({"999, EUR, 9.99 EUR", "500, JPY, 500 JPY", "12345, KWD, 12.345 KWD"})
        void remindsOfARenewalWithThePriceInTheCurrencysOwnDecimals(long minor, String currency, String price) {
            givenActive(verified);

            listener.onRenewalDue(new LicenseSubscriptionRenewalDueEvent(subscriptionId, UUID.randomUUID(), "Pro",
                    LicenseTarget.USER, verified.getId(), verified.getId(), Instant.parse("2027-02-01T00:00:00Z"),
                    minor, currency));

            assertThat(sentEmail().templateData()).containsEntry("price", price)
                    .containsEntry("renewsAt", "1 February 2027");
        }

        @Test
        void tellsWhetherSomeoneCanceledOrTheGroupWentAway() {
            givenActive(verified);

            listener.onCanceled(new LicenseSubscriptionCanceledEvent(subscriptionId, UUID.randomUUID(), "Pro",
                    LicenseTarget.GROUP, UUID.randomUUID(), verified.getId(), null, Instant.parse("2027-02-01T00:00:00Z")));

            assertThat(sentEmail().templateData()).containsEntry("isGroupRemoved", true);
        }

        @Test
        void emailsAnExpiryButNoOtherEnd() {
            givenActive(verified);

            listener.onEnded(ended("SWITCHED", verified.getId()));
            then(producer).shouldHaveNoInteractions();
            listener.onEnded(ended("EXPIRED", verified.getId()));

            assertThat(sentEmail().type()).isEqualTo(NotificationEmailType.LICENSE_SUBSCRIPTION_EXPIRED);
        }

        @Test
        void sendsNothingWhenThereIsNobodyToTellOrTheyAreNotVerified() {
            givenActive(unverified);

            listener.onEnded(ended("EXPIRED", null));
            listener.onEnded(ended("EXPIRED", unverified.getId()));

            then(producer).shouldHaveNoInteractions();
        }

        private LicenseSubscriptionEndedEvent ended(String status, UUID notify) {
            return new LicenseSubscriptionEndedEvent(subscriptionId, UUID.randomUUID(), "Pro", LicenseTarget.USER,
                    UUID.randomUUID(), notify, status, "PERIOD_ENDED", Instant.now());
        }
    }

    @Nested
    class SupportTickets {

        private final SupportTicketEventListener listener = new SupportTicketEventListener(users, producer);
        private final UUID ticketId = UUID.randomUUID();

        SupportTickets() {
            ReflectionTestUtils.setField(listener, "frontendUrl", "https://app.example");
        }

        @Test
        void tellsTheCustomerTheirTicketWasOpenedAndWhetherStaffOpenedIt() {
            givenActive(verified);

            listener.onSupportTicketCreated(new SupportTicketCreatedEvent(ticketId, "TH-7", UUID.randomUUID(),
                    verified.getId(), UUID.randomUUID(), "Refund", "BILLING"));

            SendEmailRequest email = sentEmail();
            assertThat(email.type()).isEqualTo(NotificationEmailType.SUPPORT_TICKET_CREATED);
            assertThat(email.templateData()).containsEntry("isOutbound", true).containsEntry("reference", "TH-7")
                    .containsEntry("ticketUrl", "https://app.example/support/tickets/" + ticketId);
        }

        @Test
        void tellsTheCustomerOfAReply() {
            givenActive(verified);

            listener.onSupportTicketReplied(new SupportTicketRepliedEvent(ticketId, "TH-7", UUID.randomUUID(),
                    verified.getId(), UUID.randomUUID(), "m1", "Refund"));

            assertThat(sentEmail().type()).isEqualTo(NotificationEmailType.SUPPORT_TICKET_REPLIED);
        }

        @ParameterizedTest(name = "{0}, closed by the customer: {1}")
        @CsvSource({"RESOLVED, false, SUPPORT_TICKET_RESOLVED", "CLOSED, false, SUPPORT_TICKET_CLOSED"})
        void emailsAResolutionOrAClosingBySomeoneElse(String status, boolean bySelf, NotificationEmailType type) {
            givenActive(verified);

            listener.onSupportTicketStatusChanged(changed(status, bySelf ? verified.getId() : UUID.randomUUID()));

            assertThat(sentEmail().type()).isEqualTo(type);
        }

        @Test
        void sendsNothingWhenTheCustomerClosedItOrForOtherStatuses() {
            listener.onSupportTicketStatusChanged(changed("CLOSED", verified.getId()));
            listener.onSupportTicketStatusChanged(changed("IN_PROGRESS", UUID.randomUUID()));

            then(producer).shouldHaveNoInteractions();
        }

        @Test
        void sendsNothingToAnUnverifiedOrUnknownCustomer() {
            givenActive(unverified);

            listener.onSupportTicketCreated(new SupportTicketCreatedEvent(ticketId, "TH-7", UUID.randomUUID(),
                    unverified.getId(), unverified.getId(), "Refund", "BILLING"));
            listener.onSupportTicketReplied(new SupportTicketRepliedEvent(ticketId, "TH-7", UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "m1", "Refund"));

            then(producer).shouldHaveNoInteractions();
        }

        private SupportTicketStatusChangedEvent changed(String to, UUID actor) {
            return new SupportTicketStatusChangedEvent(ticketId, "TH-7", UUID.randomUUID(), verified.getId(), actor,
                    "OPEN", to, "Refund");
        }
    }
}
