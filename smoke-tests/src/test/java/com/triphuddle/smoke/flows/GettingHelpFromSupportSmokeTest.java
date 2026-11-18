package com.triphuddle.smoke.flows;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.smoke.support.Actor;
import com.triphuddle.smoke.support.Actors;
import com.triphuddle.smoke.support.Flow;
import com.triphuddle.smoke.support.Http;
import com.triphuddle.smoke.support.Mailbox;
import com.triphuddle.smoke.support.Requirement;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
@Flow("Getting help from support")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GettingHelpFromSupportSmokeTest {

    static final String TICKETS = "/api/v1/chats/support-tickets";

    /** A customer who verified their email through the link they were sent, as emails go only to them. */
    static Actor verifiedCustomer() {
        Actor customer = Actors.customer();
        String link = Mailbox.await(customer.email(), "Verify").link("verify-email");
        Http.send("GET", link, null, null);
        return customer;
    }

    static Map<String, Object> message(String body, boolean isInternalNote) {
        return Map.of("clientMessageId", UUID.randomUUID().toString(), "body", body, "isInternalNote",
                isInternalNote);
    }

    @Test
    @Requirement({"FR-10", "FR-28", "FR-1", "FR-2", "CHT-8"})
    void a_customers_ticket_is_claimed_answered_resolved_and_rated() {
        Actor customer = verifiedCustomer();
        Actor agent = Actors.support();

        JsonNode ticket = customer.post(TICKETS, Map.of("subject", "Cannot pay", "category", "PAYMENT",
                "clientMessageId", UUID.randomUUID().toString(), "body", "My card is refused")).expect(201).body();
        String id = ticket.get("id").asText();
        String chat = "/api/v1/chats/conversations/" + ticket.get("conversationId").asText() + "/messages";
        Mailbox.await(customer.email(), "support ticket");

        agent.post(TICKETS + "/" + id + "/claim", null).expect(200);
        agent.post(chat, message("Checking with the bank", true)).expect(201);
        agent.post(chat, message("Please try again now", false)).expect(201);
        Mailbox.await(customer.email(), "Support replied");

        JsonNode history = customer.get(chat).expect(200).body().get("data");
        assertThat(history.findValuesAsText("body")).contains("Please try again now")
                .doesNotContain("Checking with the bank");

        agent.patch(TICKETS + "/" + id + "/status", Map.of("status", "RESOLVED")).expect(200);
        customer.put(TICKETS + "/" + id + "/rating", Map.of("rating", 5, "comment", "Quick")).expect(200);
        assertThat(customer.get(TICKETS + "/" + id).expect(200).body().get("rating").asInt()).isEqualTo(5);
    }

    @Test
    @Requirement("FR-10")
    void a_customer_has_at_most_three_ongoing_tickets() {
        Actor customer = Actors.customer();
        for (int i = 0; i < 3; i++) {
            customer.post(TICKETS, Map.of("subject", "Ticket " + i, "category", "OTHER", "clientMessageId",
                    UUID.randomUUID().toString(), "body", "Help")).expect(201);
        }

        assertThat(customer.post(TICKETS, Map.of("subject", "One more", "category", "OTHER", "clientMessageId",
                UUID.randomUUID().toString(), "body", "Help")).status()).isEqualTo(422);
    }
}
