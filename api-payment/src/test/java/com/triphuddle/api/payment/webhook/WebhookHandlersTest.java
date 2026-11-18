package com.triphuddle.api.payment.webhook;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.entity.StripeEvent;
import com.triphuddle.api.payment.entity.StripeEventStatus;
import com.triphuddle.api.payment.repository.StripeEventRepository;
import com.triphuddle.api.payment.service.BillingSubscriptionService;
import com.triphuddle.api.payment.service.StripeBillingService;
import com.triphuddle.api.payment.service.StripeEventProcessor;
import com.triphuddle.stripe.webhook.StripeWebhooks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class WebhookHandlersTest {

    private static Event event(String type, String object) {
        return ApiResource.GSON.fromJson("""
                {"id": "evt_1", "object": "event", "type": "%s", "api_version": "%s", "created": 1767225600,
                 "data": {"object": %s}}""".formatted(type, Stripe.API_VERSION, object), Event.class);
    }

    @Test
    void aCheckoutThatIsNoSubscriptionOrHasNoReferenceIsIgnoredAndAnUnpaidOneWaits() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        CheckoutSessionEventHandler handler = new CheckoutSessionEventHandler(billing);
        UUID id = UUID.randomUUID();

        handler.handle(event("checkout.session.completed", "{\"id\": \"cs_1\", \"object\": \"checkout.session\", "
                + "\"mode\": \"payment\", \"client_reference_id\": \"" + id + "\"}"));
        handler.handle(event("checkout.session.completed", "{\"id\": \"cs_2\", \"object\": \"checkout.session\", "
                + "\"mode\": \"subscription\"}"));
        handler.handle(event("checkout.session.completed", "{\"id\": \"cs_3\", \"object\": \"checkout.session\", "
                + "\"mode\": \"subscription\", \"client_reference_id\": \"" + id + "\", \"payment_status\": \"unpaid\"}"));
        then(billing).shouldHaveNoInteractions();

        handler.handle(event("checkout.session.async_payment_succeeded", "{\"id\": \"cs_4\", \"object\": "
                + "\"checkout.session\", \"mode\": \"subscription\", \"client_reference_id\": \"" + id + "\", "
                + "\"payment_status\": \"no_payment_required\", \"customer\": \"cus\", \"subscription\": \"sub\"}"));
        handler.handle(event("checkout.session.expired", "{\"id\": \"cs_5\", \"object\": \"checkout.session\", "
                + "\"mode\": \"subscription\", \"client_reference_id\": \"" + id + "\"}"));
        then(billing).should().onCheckoutPaid(id, "cs_4", "cus", "sub");
        then(billing).should().onCheckoutExpired(id, "cs_5");
    }

    @Test
    void anInvoiceOutsideASubscriptionIsIgnoredAndMissingAmountsReadAsZero() {
        StripeBillingService billing = mock(StripeBillingService.class);
        InvoiceEventHandler handler = new InvoiceEventHandler(billing);

        handler.handle(event("invoice.paid", "{\"id\": \"in_1\", \"object\": \"invoice\"}"));
        handler.handle(event("invoice.voided", "{\"id\": \"in_2\", \"object\": \"invoice\", \"currency\": \"eur\", "
                + "\"parent\": {\"type\": \"subscription_details\", \"subscription_details\": "
                + "{\"subscription\": \"sub_1\"}}}"));
        handler.handle(event("invoice.marked_uncollectible", "{\"id\": \"in_3\", \"object\": \"invoice\", "
                + "\"currency\": \"eur\", \"parent\": {\"type\": \"quote_details\"}}"));

        then(billing).should().onInvoiceChanged(org.mockito.ArgumentMatchers.argThat(invoice ->
                invoice.id().equals("in_2") && invoice.totalMinor() == 0 && invoice.paidAt() == null));
        then(billing).should(org.mockito.Mockito.times(1)).onInvoiceChanged(any());
        assertThat(handler.eventTypes()).contains("invoice.payment_failed");
    }

    @Test
    void aDeletionWithoutAnEndTimeUsesTheEventsTime() {
        StripeBillingService billing = mock(StripeBillingService.class);

        new SubscriptionEventHandler(billing).handle(event("customer.subscription.deleted",
                "{\"id\": \"sub_1\", \"object\": \"subscription\"}"));

        then(billing).should().onStripeSubscriptionDeleted("sub_1", Instant.ofEpochSecond(1767225600), null);
    }

    @Test
    void anEventWhoseObjectCannotBeReadIsAnError() {
        Event unreadable = ApiResource.GSON.fromJson("""
                {"id": "evt_2", "object": "event", "type": "invoice.paid", "api_version": "2020-08-27",
                 "data": {"object": {"id": "in_9", "object": "invoice"}}}""", Event.class);

        assertThatThrownBy(() -> StripeEvents.objectOf(unreadable)).hasMessageContaining("no readable object");
    }

    @Test
    void twoHandlersForOneTypeStopTheServiceFromStarting() {
        StripeEventHandler first = handler(Set.of("invoice.paid"));
        StripeEventHandler second = handler(Set.of("invoice.paid"));
        @SuppressWarnings("unchecked")
        ObjectProvider<StripeEventHandler> handlers = mock(ObjectProvider.class);
        given(handlers.orderedStream()).willReturn(Stream.of(first, second));

        assertThatThrownBy(() -> new StripeEventProcessor(mock(StripeEventRepository.class), mock(StripeWebhooks.class),
                properties(), handlers)).hasMessageContaining("has two handlers");
    }

    @Test
    void anEventInAVersionStripeJavaCannotReadFailsWithoutRetries() {
        StripeEventRepository repository = mock(StripeEventRepository.class);
        StripeWebhooks webhooks = mock(StripeWebhooks.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StripeEventHandler> handlers = mock(ObjectProvider.class);
        given(handlers.orderedStream()).willReturn(Stream.of(handler(Set.of("invoice.paid"))));
        StripeEvent stored = new StripeEvent();
        stored.setId(UUID.randomUUID());
        stored.setEventType("invoice.paid");
        stored.setStatus(StripeEventStatus.PENDING);
        stored.setPayload("{}");
        given(repository.lockDue(any(), any())).willReturn(List.of(stored));
        Event old = new Event();
        old.setApiVersion("2020-08-27");
        given(webhooks.parse("{}")).willReturn(old);

        new StripeEventProcessor(repository, webhooks, properties(), handlers).processNext(Instant.now());

        assertThat(stored.getStatus()).isEqualTo(StripeEventStatus.FAILED);
        assertThat(stored.getLastError()).contains("2020-08-27");
    }

    private static PaymentProperties properties() {
        return new PaymentProperties(1, 10, 1, null, "s", "c", "u", 1, 24);
    }

    private static StripeEventHandler handler(Set<String> types) {
        return new StripeEventHandler() {
            @Override
            public Set<String> eventTypes() {
                return types;
            }

            @Override
            public void handle(Event event) {
            }
        };
    }
}
