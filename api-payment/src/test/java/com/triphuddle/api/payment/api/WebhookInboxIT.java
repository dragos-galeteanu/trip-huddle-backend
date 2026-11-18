package com.triphuddle.api.payment.api;

import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-1: every webhook is verified, stored once by its Stripe ID, and processed from the inbox with retries.
 */
class WebhookInboxIT extends PaymentApiSupport {

    private static final String EVENTS = "/api/v1/payments/stripe-events";

    @Test
    void storesASignedEventOnceHoweverOftenStripeSendsIt() throws Exception {
        String payload = event("product.created", "{\"id\": \"prod_1\", \"object\": \"product\"}");
        String stripeId = json.readTree(payload).path("id").asText();

        webhook(payload).andExpect(status().isOk());
        webhook(payload).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from payment_stripe_events where stripe_event_id = ?",
                Integer.class, stripeId)).isEqualTo(1);
    }

    @Test
    void refusesAnUnsignedOrForgedEventAndStoresNothing() throws Exception {
        String payload = event("invoice.paid", "{\"id\": \"in_x\", \"object\": \"invoice\"}");
        String stripeId = json.readTree(payload).path("id").asText();

        mvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("Stripe-Signature", "t=1,v1=deadbeef")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from payment_stripe_events where stripe_event_id = ?",
                Integer.class, stripeId)).isZero();
    }

    @Test
    void ignoresTypesNobodyHandlesAndRetriesAFailingOneWithGrowingDelaysThenStaffReplay() throws Exception {
        String ignored = event("product.created", "{\"id\": \"prod_2\", \"object\": \"product\"}");
        // An invoice for a first payment whose checkout event hasn't come is retried until it has.
        String early = event("invoice.paid", invoice("in_early_" + UUID.randomUUID(), "sub_unknown_" + UUID.randomUUID(),
                "subscription_create", "paid", 999));
        webhook(ignored).andExpect(status().isOk());
        webhook(early).andExpect(status().isOk());

        stripeEventJob.processDue();

        assertThat(statusOf(ignored)).isEqualTo("IGNORED");
        assertThat(statusOf(early)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempts from payment_stripe_events where stripe_event_id = ?",
                Integer.class, idOf(early))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select next_attempt_at > now() + interval '20 seconds' from "
                + "payment_stripe_events where stripe_event_id = ?", Boolean.class, idOf(early))).isTrue();

        jdbc.update("update payment_stripe_events set attempts = 9, next_attempt_at = now() where stripe_event_id = ?",
                idOf(early));
        stripeEventJob.processDue();
        assertThat(statusOf(early)).isEqualTo("FAILED");

        UUID id = jdbc.queryForObject("select id from payment_stripe_events where stripe_event_id = ?", UUID.class,
                idOf(early));
        mvc.perform(post(EVENTS + "/" + id + "/replay").with(Caller.INSPECTOR.request()))
                .andExpect(status().isForbidden());
        mvc.perform(post(EVENTS + "/" + id + "/replay").with(Caller.SUPPORT.request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
        mvc.perform(post(EVENTS + "/" + id + "/replay").with(Caller.SUPPORT.request()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void staffPageThroughTheInboxAndReadAnEventsPayload() throws Exception {
        for (int i = 0; i < 3; i++) {
            webhook(event("product.updated", "{\"id\": \"prod_p" + i + "\", \"object\": \"product\"}"))
                    .andExpect(status().isOk());
        }

        var first = body(mvc.perform(get(EVENTS).param("limit", "2").param("eventType", "product.updated")
                .with(Caller.INSPECTOR.request())).andExpect(status().isOk()));
        assertThat(first.path("data").size()).isEqualTo(2);
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        var next = body(mvc.perform(get(EVENTS).param("limit", "2").param("eventType", "product.updated")
                .param("cursor", first.path("nextCursor").asText()).with(Caller.INSPECTOR.request())));
        assertThat(next.path("data").get(0).path("id").asText())
                .isNotIn(first.path("data").findValuesAsText("id"));

        String id = first.path("data").get(0).path("id").asText();
        mvc.perform(get(EVENTS + "/" + id).with(Caller.INSPECTOR.request()))
                .andExpect(jsonPath("$.payload").isNotEmpty());
        mvc.perform(get(EVENTS).with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        mvc.perform(get(EVENTS).param("cursor", "garbage!").with(Caller.INSPECTOR.request()))
                .andExpect(status().isBadRequest());
    }

    private String idOf(String payload) throws Exception {
        return json.readTree(payload).path("id").asText();
    }

    private String statusOf(String payload) throws Exception {
        return jdbc.queryForObject("select status from payment_stripe_events where stripe_event_id = ?", String.class,
                idOf(payload));
    }
}
