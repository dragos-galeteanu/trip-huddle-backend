package com.triphuddle.api.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.chat.SupportTicketCreatedEvent;
import com.triphuddle.amqp.chat.SupportTicketRepliedEvent;
import com.triphuddle.amqp.chat.SupportTicketStatusChangedEvent;
import com.triphuddle.api.chat.config.ChatStompAuthChannelInterceptor;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.job.SupportTicketAutoCloseJob;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CHT-6 and CHT-7: support tickets, their status rules and internal notes, over HTTP. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SupportTicketIT extends ChatApiSupport {

    @Autowired SupportTicketAutoCloseJob autoCloseJob;

    JsonNode open(UUID customer, String category) throws Exception {
        return body(call(post(TICKETS), Caller.CUSTOMER, customer, ticketRequest(category))
                .andExpect(status().isCreated()));
    }

    static Map<String, Object> ticketRequest(String category) {
        Map<String, Object> request = new HashMap<>();
        request.put("subject", "  Cannot pay  ");
        request.put("category", category);
        request.put("clientMessageId", UUID.randomUUID().toString());
        request.put("body", "My card is refused");
        return request;
    }

    JsonNode claimed(UUID customer, UUID agent) throws Exception {
        JsonNode ticket = open(customer, "PAYMENT");
        call(post(TICKETS + "/" + ticket.get("id").asText() + "/claim"), Caller.SUPPORT, agent)
                .andExpect(status().isOk());
        return ticket;
    }

    void move(JsonNode ticket, Caller caller, UUID userId, String target, int expected) throws Exception {
        call(patch(TICKETS + "/" + ticket.get("id").asText() + "/status"), caller, userId, Map.of("status", target))
                .andExpect(status().is(expected));
    }

    String statusOf(JsonNode ticket) throws Exception {
        return body(call(get(TICKETS + "/" + ticket.get("id").asText()), Caller.SUPPORT, UUID.randomUUID()))
                .get("status").asText();
    }

    UUID chatOf(JsonNode ticket) {
        return UUID.fromString(ticket.get("conversationId").asText());
    }

    @Nested
    class Opening {

        @Test
        void a_customer_opens_a_ticket_with_its_first_message() throws Exception {
            UUID customer = user();

            JsonNode ticket = open(customer, "PAYMENT");

            assertThat(ticket.get("status").asText()).isEqualTo("OPEN");
            assertThat(ticket.get("subject").asText()).isEqualTo("Cannot pay");
            assertThat(ticket.get("reference").asText()).startsWith("TH-");
            assertThat(ticket.get("priority").isNull()).isTrue();
            assertThat(history(chatOf(ticket), Caller.CUSTOMER, customer).findValuesAsText("body"))
                    .containsExactly("My card is refused", "Ticket %s opened".formatted(ticket.get("reference").asText()));
            published.assertPublishedCount(SupportTicketCreatedEvent.class, 1);
            assertThat(broker.toTopic(ChatStompAuthChannelInterceptor.SUPPORT_QUEUE_TOPIC, ChatEventType.TICKET_UPDATED))
                    .hasSize(1);
            call(get(CONVERSATIONS + "/" + chatOf(ticket)), Caller.CUSTOMER, customer)
                    .andExpect(jsonPath("$.ticket.reference").value(ticket.get("reference").asText()));
        }

        @Test
        void a_safety_ticket_starts_with_high_priority() throws Exception {
            JsonNode ticket = open(user(), "SAFETY");

            call(get(TICKETS + "/" + ticket.get("id").asText()), Caller.SUPPORT, UUID.randomUUID())
                    .andExpect(jsonPath("$.priority").value("HIGH"));
        }

        @Test
        void retrying_with_the_same_client_message_id_returns_the_ticket_already_opened() throws Exception {
            UUID customer = user();
            Map<String, Object> request = ticketRequest("BUG");

            String first = body(call(post(TICKETS), Caller.CUSTOMER, customer, request)).get("id").asText();
            String again = body(call(post(TICKETS), Caller.CUSTOMER, customer, request)
                    .andExpect(status().isCreated())).get("id").asText();

            assertThat(again).isEqualTo(first);
            published.assertPublishedCount(SupportTicketCreatedEvent.class, 1);
        }

        @Test
        void a_customer_has_at_most_three_ongoing_tickets() throws Exception {
            UUID customer = user();
            JsonNode first = open(customer, "BUG");
            open(customer, "BUG");
            open(customer, "BUG");

            call(post(TICKETS), Caller.CUSTOMER, customer, ticketRequest("BUG"))
                    .andExpect(status().isUnprocessableEntity());

            move(first, Caller.CUSTOMER, customer, "CLOSED", 200);
            open(customer, "BUG");
        }

        @Test
        void a_disabled_account_cannot_open_tickets() throws Exception {
            UUID customer = user();
            userEvents.onUserDisabled(new com.triphuddle.amqp.user.UserDisabledEvent(customer));

            call(post(TICKETS), Caller.CUSTOMER, customer, ticketRequest("ACCOUNT"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void a_ticket_links_only_what_the_customer_can_see() throws Exception {
            UUID customer = user();
            UUID friend = user();
            UUID chat = privateChat(customer, friend);
            String message = say(chat, friend, "Rude words");
            UUID groupId = group(customer);
            Map<String, Object> request = ticketRequest("SAFETY");
            request.put("conversationId", chat);
            request.put("messageId", message);
            request.put("groupId", groupId);

            call(post(TICKETS), Caller.CUSTOMER, customer, request)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.linkedConversationId").value(chat.toString()))
                    .andExpect(jsonPath("$.messageId").value(message))
                    .andExpect(jsonPath("$.groupId").value(groupId.toString()));

            UUID outsider = user();
            for (Map.Entry<String, Object> link : Map.<String, Object>of("conversationId", chat, "messageId", message,
                    "groupId", groupId, "messageReportId", UUID.randomUUID()).entrySet()) {
                Map<String, Object> foreign = ticketRequest("SAFETY");
                foreign.put(link.getKey(), link.getValue());
                call(post(TICKETS), Caller.CUSTOMER, outsider, foreign).andExpect(status().isNotFound());
            }
        }

        @Test
        void staff_open_a_ticket_towards_a_user_and_own_it_from_the_start() throws Exception {
            UUID customer = user();
            UUID agent = user();
            Map<String, Object> request = ticketRequest("ACCOUNT");
            request.put("userId", customer);

            JsonNode ticket = body(call(post(TICKETS), Caller.SUPPORT, agent, request).andExpect(status().isCreated()));

            assertThat(ticket.get("status").asText()).isEqualTo("IN_PROGRESS");
            assertThat(ticket.get("assigneeId").asText()).isEqualTo(agent.toString());
            assertThat(ticket.get("requesterId").asText()).isEqualTo(customer.toString());
            assertThat(history(chatOf(ticket), Caller.CUSTOMER, customer).findValuesAsText("body"))
                    .contains("My card is refused", "A support agent took the ticket");

            call(post(TICKETS), Caller.CUSTOMER, user(), request).andExpect(status().isForbidden());
            request.put("clientMessageId", UUID.randomUUID().toString());
            request.put("userId", agent);
            call(post(TICKETS), Caller.SUPPORT, agent, request).andExpect(status().isUnprocessableEntity());
            request.put("userId", UUID.randomUUID());
            call(post(TICKETS), Caller.SUPPORT, agent, request).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Handling {

        @Test
        void claiming_takes_the_ticket_into_progress_and_nobody_else_may_claim_it() throws Exception {
            UUID customer = user();
            UUID agent = user();
            JsonNode ticket = open(customer, "PAYMENT");
            String id = ticket.get("id").asText();

            call(post(TICKETS + "/" + id + "/claim"), Caller.SUPPORT, agent)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.assigneeId").value(agent.toString()));
            call(post(TICKETS + "/" + id + "/claim"), Caller.SUPPORT, agent).andExpect(status().isOk());
            call(post(TICKETS + "/" + id + "/claim"), Caller.SUPPORT, user()).andExpect(status().isUnprocessableEntity());
            call(post(TICKETS + "/" + id + "/claim"), Caller.CUSTOMER, user()).andExpect(status().isForbidden());
            call(post(TICKETS + "/" + id + "/claim"), Caller.SUPPORT, customer)
                    .andExpect(status().isUnprocessableEntity());

            published.assertPublished(new SupportTicketStatusChangedEvent(UUID.fromString(id),
                    ticket.get("reference").asText(), chatOf(ticket), customer, agent, "OPEN", "IN_PROGRESS",
                    "Cannot pay"), "chat.support-ticket.status-changed.queue");
        }

        @Test
        void staff_must_claim_before_working_and_follow_the_status_rules() throws Exception {
            UUID agent = user();
            JsonNode ticket = open(user(), "BUG");

            move(ticket, Caller.SUPPORT, agent, "WAITING_ON_CUSTOMER", 422);
            move(ticket, Caller.SUPPORT, agent, "RESOLVED", 422);
            move(ticket, Caller.SUPPORT, agent, "OPEN", 200);
            move(ticket, Caller.SUPPORT, agent, "CLOSED", 200);
            assertThat(statusOf(ticket)).isEqualTo("CLOSED");

            call(post(TICKETS + "/" + ticket.get("id").asText() + "/join"), Caller.SUPPORT, agent)
                    .andExpect(status().isUnprocessableEntity());
            move(ticket, Caller.SUPPORT, agent, "IN_PROGRESS", 200);
            assertThat(statusOf(ticket)).isEqualTo("OPEN");
        }

        @Test
        void the_customers_reply_brings_a_waiting_ticket_back_into_progress() throws Exception {
            UUID customer = user();
            UUID agent = user();
            JsonNode ticket = claimed(customer, agent);
            move(ticket, Caller.SUPPORT, agent, "WAITING_ON_CUSTOMER", 200);

            say(chatOf(ticket), customer, "Here is the screenshot");

            assertThat(statusOf(ticket)).isEqualTo("IN_PROGRESS");
        }

        @Test
        void the_first_staff_reply_after_the_customer_wrote_is_announced_once() throws Exception {
            UUID customer = user();
            UUID agent = user();
            JsonNode ticket = claimed(customer, agent);
            UUID chat = chatOf(ticket);
            published.reset();

            say(chat, Caller.SUPPORT, agent, "Looking into it");
            say(chat, Caller.SUPPORT, agent, "Still looking");

            published.assertPublishedCount(SupportTicketRepliedEvent.class, 1);
            assertThat(body(call(get(TICKETS + "/" + ticket.get("id").asText()), Caller.CUSTOMER, customer))
                    .get("firstResponseAt").isNull()).isFalse();

            say(chat, customer, "Thanks");
            say(chat, Caller.SUPPORT, agent, "Fixed");
            published.assertPublishedCount(SupportTicketRepliedEvent.class, 2);
        }

        @Test
        void joining_leaving_and_releasing_hand_the_ticket_back_to_the_queue() throws Exception {
            UUID customer = user();
            UUID agent = user();
            UUID helper = user();
            String id = claimed(customer, agent).get("id").asText();

            call(post(TICKETS + "/" + id + "/join"), Caller.SUPPORT, helper).andExpect(status().isOk());
            call(post(TICKETS + "/" + id + "/join"), Caller.SUPPORT, helper).andExpect(status().isOk());
            call(post(TICKETS + "/" + id + "/release"), Caller.SUPPORT, helper).andExpect(status().isForbidden());
            call(post(TICKETS + "/" + id + "/leave"), Caller.SUPPORT, helper).andExpect(status().isOk());
            call(post(TICKETS + "/" + id + "/leave"), Caller.SUPPORT, helper).andExpect(status().isUnprocessableEntity());
            call(post(TICKETS + "/" + id + "/join"), Caller.SUPPORT, helper).andExpect(status().isOk());

            call(post(TICKETS + "/" + id + "/release"), Caller.SUPPORT, agent)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.assigneeId").doesNotExist());
            call(post(TICKETS + "/" + id + "/release"), Caller.ADMIN, user()).andExpect(status().isUnprocessableEntity());

            call(post(TICKETS + "/" + id + "/claim"), Caller.SUPPORT, helper).andExpect(status().isOk());
            call(post(TICKETS + "/" + id + "/leave"), Caller.SUPPORT, helper)
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.assigneeId").doesNotExist());

            List<String> types = body(call(get(TICKETS + "/" + id + "/events"), Caller.SUPPORT, agent))
                    .findValuesAsText("type");
            assertThat(types).contains("CREATED", "ASSIGNED", "STAFF_JOINED", "STAFF_LEFT", "UNASSIGNED");
            call(get(TICKETS + "/" + id + "/events"), Caller.CUSTOMER, customer).andExpect(status().isForbidden());
        }

        @Test
        void staff_change_priority_and_category_and_only_real_changes_are_recorded() throws Exception {
            UUID agent = user();
            String id = open(user(), "OTHER").get("id").asText();

            call(patch(TICKETS + "/" + id), Caller.SUPPORT, agent, Map.of("priority", "URGENT", "category", "BUG"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priority").value("URGENT"))
                    .andExpect(jsonPath("$.category").value("BUG"));
            call(patch(TICKETS + "/" + id), Caller.SUPPORT, agent, Map.of("priority", "URGENT"))
                    .andExpect(status().isOk());
            call(patch(TICKETS + "/" + id), Caller.CUSTOMER, agent, Map.of("priority", "LOW"))
                    .andExpect(status().isForbidden());

            List<String> types = body(call(get(TICKETS + "/" + id + "/events"), Caller.SUPPORT, agent))
                    .findValuesAsText("type");
            assertThat(types).containsExactly("CREATED", "PRIORITY_CHANGED", "CATEGORY_CHANGED");
        }
    }

    @Nested
    class Internal_notes {

        @Test
        void an_internal_note_never_reaches_the_customer() throws Exception {
            UUID customer = user();
            UUID agent = user();
            UUID chat = chatOf(claimed(customer, agent));
            broker.reset();

            String note = body(sendMessage(chat, Caller.SUPPORT, agent, Map.of("clientMessageId", "n1",
                    "body", "Card flagged by fraud", "isInternalNote", true)).andExpect(status().isCreated()))
                    .get("id").asText();

            assertThat(history(chat, Caller.CUSTOMER, customer).findValuesAsText("id")).doesNotContain(note);
            assertThat(history(chat, Caller.SUPPORT, agent).findValuesAsText("id")).contains(note);
            assertThat(history(chat, Caller.SUPPORT, user()).findValuesAsText("id")).contains(note);
            assertThat(broker.to(customer, ChatEventType.MESSAGE_CREATED)).isEmpty();
            assertThat(broker.to(agent, ChatEventType.MESSAGE_CREATED)).hasSize(1);
            call(get(MESSAGES + "/" + note), Caller.CUSTOMER, customer).andExpect(status().isForbidden());
            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, customer)
                    .andExpect(jsonPath("$.lastMessagePreview").value(org.hamcrest.Matchers.not("Card flagged by fraud")));
        }

        @Test
        void only_staff_write_notes_and_a_reply_to_a_note_is_a_note() throws Exception {
            UUID customer = user();
            UUID agent = user();
            UUID chat = chatOf(claimed(customer, agent));
            String note = body(sendMessage(chat, Caller.SUPPORT, agent, Map.of("clientMessageId", "n1",
                    "body", "Internal", "isInternalNote", true))).get("id").asText();

            sendMessage(chat, Caller.CUSTOMER, customer, Map.of("clientMessageId", "c1", "body", "Me too",
                    "isInternalNote", true)).andExpect(status().isUnprocessableEntity());
            sendMessage(chat, Caller.SUPPORT, agent, Map.of("clientMessageId", "n2", "body", "Public",
                    "replyToMessageId", note)).andExpect(status().isUnprocessableEntity());
            sendMessage(chat, Caller.SUPPORT, agent, Map.of("clientMessageId", "n3", "body", "Noted",
                    "replyToMessageId", note, "isInternalNote", true)).andExpect(status().isCreated());
        }

        @Test
        void staff_who_lost_their_role_can_no_longer_write() throws Exception {
            UUID agent = user();
            UUID chat = chatOf(claimed(user(), agent));

            sendMessage(chat, Caller.CUSTOMER, agent, Map.of("clientMessageId", "x", "body", "Hi"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void a_customer_cannot_mute_their_support_chat_but_staff_can() throws Exception {
            UUID customer = user();
            UUID agent = user();
            UUID chat = chatOf(claimed(customer, agent));

            call(put(CONVERSATIONS + "/" + chat + "/mute"), Caller.CUSTOMER, customer, Map.of("isMuted", true))
                    .andExpect(status().isUnprocessableEntity());
            call(put(CONVERSATIONS + "/" + chat + "/mute"), Caller.SUPPORT, agent, Map.of("isMuted", true))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Resolving_closing_and_rating {

        @Test
        void the_customer_closes_their_ticket_or_reopens_it_while_it_is_resolved() throws Exception {
            UUID customer = user();
            UUID agent = user();
            JsonNode ticket = claimed(customer, agent);

            move(ticket, Caller.CUSTOMER, customer, "WAITING_ON_CUSTOMER", 422);
            move(ticket, Caller.SUPPORT, agent, "RESOLVED", 200);
            move(ticket, Caller.CUSTOMER, customer, "OPEN", 200);
            assertThat(statusOf(ticket)).isEqualTo("IN_PROGRESS");

            move(ticket, Caller.CUSTOMER, customer, "CLOSED", 200);
            move(ticket, Caller.CUSTOMER, customer, "OPEN", 422);
            sendMessage(chatOf(ticket), Caller.CUSTOMER, customer, Map.of("clientMessageId", "late", "body", "Hey"))
                    .andExpect(status().isUnprocessableEntity());
            move(ticket, Caller.CUSTOMER, user(), "CLOSED", 403);
            move(ticket, Caller.CUSTOMER, customer, "CLOSED", 200);
        }

        @Test
        void staff_reopen_a_closed_ticket_and_the_chat_is_writable_again() throws Exception {
            UUID customer = user();
            UUID agent = user();
            JsonNode ticket = claimed(customer, agent);
            move(ticket, Caller.SUPPORT, agent, "CLOSED", 200);

            move(ticket, Caller.SUPPORT, agent, "OPEN", 200);

            assertThat(statusOf(ticket)).isEqualTo("IN_PROGRESS");
            say(chatOf(ticket), customer, "Thanks for reopening");
        }

        @Test
        void a_ticket_is_rated_from_1_to_5_once_it_is_resolved() throws Exception {
            UUID customer = user();
            UUID agent = user();
            JsonNode ticket = claimed(customer, agent);
            String rating = TICKETS + "/" + ticket.get("id").asText() + "/rating";

            call(put(rating), Caller.CUSTOMER, customer, Map.of("rating", 5)).andExpect(status().isUnprocessableEntity());
            move(ticket, Caller.SUPPORT, agent, "RESOLVED", 200);
            call(put(rating), Caller.CUSTOMER, customer, Map.of("rating", 6)).andExpect(status().isBadRequest());
            call(put(rating), Caller.CUSTOMER, customer, Map.of("rating", 0)).andExpect(status().isBadRequest());
            call(put(rating), Caller.SUPPORT, agent, Map.of("rating", 5)).andExpect(status().isForbidden());
            call(put(rating), Caller.CUSTOMER, customer, Map.of("rating", 4, "comment", "  Quick  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value(4))
                    .andExpect(jsonPath("$.ratingComment").value("Quick"));
            call(put(rating), Caller.CUSTOMER, customer, Map.of("rating", 5, "comment", " "))
                    .andExpect(jsonPath("$.rating").value(5))
                    .andExpect(jsonPath("$.ratingComment").doesNotExist());
        }

        @Test
        void a_resolved_ticket_closes_on_its_own_after_seven_days() throws Exception {
            UUID agent = user();
            JsonNode stale = claimed(user(), agent);
            JsonNode fresh = claimed(user(), agent);
            move(stale, Caller.SUPPORT, agent, "RESOLVED", 200);
            move(fresh, Caller.SUPPORT, agent, "RESOLVED", 200);
            jdbc.update("update support_tickets set resolved_at = ? where id = ?",
                    Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)), UUID.fromString(stale.get("id").asText()));

            autoCloseJob.closeResolvedTickets();

            assertThat(statusOf(stale)).isEqualTo("CLOSED");
            assertThat(statusOf(fresh)).isEqualTo("RESOLVED");
            call(get(CONVERSATIONS + "/" + chatOf(stale)), Caller.SUPPORT, agent)
                    .andExpect(jsonPath("$.isReadOnly").value(true));
        }
    }

    @Nested
    class Lists {

        @Test
        void the_queue_is_for_staff_and_puts_the_most_urgent_first() throws Exception {
            UUID agent = user();
            JsonNode normal = open(user(), "BUG");
            JsonNode safety = open(user(), "SAFETY");
            JsonNode mine = claimed(user(), agent);

            assertThat(body(call(get(TICKETS), Caller.SUPPORT, agent)).get("data").findValuesAsText("id"))
                    .first().isEqualTo(safety.get("id").asText());
            assertThat(body(call(get(TICKETS + "?assigneeId=" + agent), Caller.SUPPORT, agent)).get("data")
                    .findValuesAsText("id")).containsExactly(mine.get("id").asText());
            assertThat(body(call(get(TICKETS + "?isUnassigned=true&status=OPEN&category=BUG"), Caller.SUPPORT, agent))
                    .get("data").findValuesAsText("id")).containsExactly(normal.get("id").asText());
            assertThat(body(call(get(TICKETS + "?priority=HIGH"), Caller.SUPPORT, agent)).get("data")
                    .findValuesAsText("id")).containsExactly(safety.get("id").asText());

            JsonNode page = body(call(get(TICKETS + "?limit=2"), Caller.ADMIN, agent));
            assertThat(page.get("hasMore").asBoolean()).isTrue();
            JsonNode rest = body(call(get(TICKETS + "?limit=2&cursor=" + page.get("nextCursor").asText()),
                    Caller.ADMIN, agent));
            assertThat(rest.get("data")).hasSize(1);
            call(get(TICKETS), Caller.CUSTOMER, agent).andExpect(status().isForbidden());
        }

        @Test
        void a_customer_lists_their_own_tickets_newest_first() throws Exception {
            UUID customer = user();
            JsonNode older = open(customer, "BUG");
            JsonNode newer = open(customer, "BUG");
            open(user(), "BUG");
            move(older, Caller.CUSTOMER, customer, "CLOSED", 200);

            JsonNode page = body(call(get(TICKETS + "/mine?limit=1"), Caller.CUSTOMER, customer));
            assertThat(page.get("data").findValuesAsText("id")).containsExactly(newer.get("id").asText());
            JsonNode rest = body(call(get(TICKETS + "/mine?limit=1&cursor=" + page.get("nextCursor").asText()),
                    Caller.CUSTOMER, customer));
            assertThat(rest.get("data").findValuesAsText("id")).containsExactly(older.get("id").asText());
            assertThat(body(call(get(TICKETS + "/mine?status=CLOSED"), Caller.CUSTOMER, customer)).get("data"))
                    .hasSize(1);
            call(get(TICKETS + "/" + older.get("id").asText()), Caller.CUSTOMER, user()).andExpect(status().isForbidden());
            call(get(TICKETS + "/" + UUID.randomUUID()), Caller.SUPPORT, customer).andExpect(status().isNotFound());
        }
    }
}
