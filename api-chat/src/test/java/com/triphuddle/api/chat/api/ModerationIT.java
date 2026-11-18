package com.triphuddle.api.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.chat.ChatMessageReportedContract;
import com.triphuddle.amqp.chat.ChatMessageReportedEvent;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CHT-5: reports on messages, one per reader while open, reviewed by staff. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ModerationIT extends ChatApiSupport {

    JsonNode report(String messageId, UUID reporter) throws Exception {
        return body(call(post(MESSAGES + "/" + messageId + "/reports"), Caller.CUSTOMER, reporter,
                Map.of("reason", "  Insults  ")).andExpect(status().isCreated()));
    }

    @Test
    void a_reader_reports_a_message_once_while_the_report_is_open() throws Exception {
        UUID ana = user();
        UUID bob = user();
        UUID chat = privateChat(ana, bob);
        String message = say(chat, bob, "You are an idiot");

        JsonNode report = report(message, ana);

        assertThat(report.get("status").asText()).isEqualTo("OPEN");
        assertThat(report.get("reason").asText()).isEqualTo("Insults");
        assertThat(report.get("createdAt").isNull()).isFalse();
        assertThat(report.get("message").isNull()).isTrue();
        published.assertPublished(new ChatMessageReportedEvent(idOf(report), message, chat, ana),
                ChatMessageReportedContract.CHAT_MESSAGE_REPORTED_QUEUE_NAME);
        call(post(MESSAGES + "/" + message + "/reports"), Caller.CUSTOMER, ana, Map.of("reason", "Again"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nobody_reports_their_own_message_one_they_cannot_read_or_one_in_a_support_chat() throws Exception {
        UUID ana = user();
        UUID bob = user();
        String message = say(privateChat(ana, bob), bob, "Hello");
        UUID agent = user();
        JsonNode ticket = body(call(post(TICKETS), Caller.CUSTOMER, ana, Map.of("subject", "Help",
                "category", "OTHER", "clientMessageId", "t1", "body", "Help me")));
        UUID supportChat = UUID.fromString(ticket.get("conversationId").asText());
        call(post(TICKETS + "/" + ticket.get("id").asText() + "/claim"), Caller.SUPPORT, agent);
        String reply = say(supportChat, Caller.SUPPORT, agent, "On it");

        call(post(MESSAGES + "/" + message + "/reports"), Caller.CUSTOMER, bob, Map.of("reason", "x"))
                .andExpect(status().isUnprocessableEntity());
        call(post(MESSAGES + "/" + message + "/reports"), Caller.CUSTOMER, user(), Map.of("reason", "x"))
                .andExpect(status().isForbidden());
        call(post(MESSAGES + "/" + reply + "/reports"), Caller.CUSTOMER, ana, Map.of("reason", "x"))
                .andExpect(status().isUnprocessableEntity());
        call(post(MESSAGES + "/" + message + "/reports"), Caller.CUSTOMER, ana, Map.of("reason", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staff_review_a_report_with_its_context_and_may_remove_the_message() throws Exception {
        UUID ana = user();
        UUID bob = user();
        UUID chat = privateChat(ana, bob);
        say(chat, ana, "Before");
        String message = say(chat, bob, "Offensive");
        say(chat, ana, "After");
        UUID id = idOf(report(message, ana));
        UUID inspector = UUID.randomUUID();

        call(get(REPORTS + "/" + id), Caller.INSPECTOR, inspector)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.body").value("Offensive"));
        assertThat(body(call(get(REPORTS + "/" + id + "/context"), Caller.SUPPORT, inspector))
                .findValuesAsText("body")).containsExactly("Before", "Offensive", "After");

        call(patch(REPORTS + "/" + id), Caller.SUPPORT, inspector, Map.of("status", "ACTION_TAKEN",
                "resolutionNote", "Removed", "isMessageRemoved", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTION_TAKEN"))
                .andExpect(jsonPath("$.resolvedByUserId").value(inspector.toString()))
                .andExpect(jsonPath("$.message.isDeleted").value(true))
                .andExpect(jsonPath("$.message.body").value("Offensive"));

        assertThat(history(chat, Caller.CUSTOMER, ana).findValuesAsText("id")).contains(message);
        call(get(MESSAGES + "/" + message), Caller.CUSTOMER, ana).andExpect(jsonPath("$.body").doesNotExist());
        call(patch(REPORTS + "/" + id), Caller.SUPPORT, inspector, Map.of("status", "DISMISSED"))
                .andExpect(status().isUnprocessableEntity());
        // Once resolved, the same reader may report it again.
        report(message, ana);
    }

    @Test
    void a_report_is_resolved_as_dismissed_or_action_taken_and_a_removed_message_stays_removed() throws Exception {
        UUID ana = user();
        UUID bob = user();
        UUID chat = privateChat(ana, bob);
        String message = say(chat, bob, "Meh");
        UUID first = idOf(report(message, ana));
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(MESSAGES + "/" + message),
                Caller.CUSTOMER, bob).andExpect(status().isOk());

        call(patch(REPORTS + "/" + first), Caller.ADMIN, user(), Map.of("status", "OPEN"))
                .andExpect(status().isUnprocessableEntity());
        call(patch(REPORTS + "/" + first), Caller.ADMIN, user(), Map.of("status", "ACTION_TAKEN",
                "isMessageRemoved", true)).andExpect(status().isOk());
        call(patch(REPORTS + "/" + UUID.randomUUID()), Caller.ADMIN, user(), Map.of("status", "DISMISSED"))
                .andExpect(status().isNotFound());
    }

    @Test
    void the_report_list_is_for_staff_pages_by_cursor_and_filters_by_status() throws Exception {
        UUID ana = user();
        UUID bob = user();
        UUID chat = privateChat(ana, bob);
        UUID first = idOf(report(say(chat, bob, "One"), ana));
        UUID second = idOf(report(say(chat, bob, "Two"), ana));
        UUID dismissed = idOf(report(say(chat, bob, "Three"), ana));
        call(patch(REPORTS + "/" + dismissed), Caller.SUPPORT, user(), Map.of("status", "DISMISSED"));

        JsonNode page = body(call(get(REPORTS + "?limit=1"), Caller.SUPPORT, user()));
        assertThat(ids(page.get("data"))).containsExactly(first.toString());
        JsonNode rest = body(call(get(REPORTS + "?limit=1&cursor=" + page.get("nextCursor").asText()),
                Caller.SUPPORT, user()));
        assertThat(ids(rest.get("data"))).containsExactly(second.toString());
        assertThat(ids(body(call(get(REPORTS + "?status=DISMISSED"), Caller.SUPPORT, user())).get("data"))).containsExactly(dismissed.toString());

        call(get(REPORTS), Caller.CUSTOMER, ana).andExpect(status().isForbidden());
        call(get(REPORTS + "/" + first), Caller.SALES, ana).andExpect(status().isForbidden());
        call(get(REPORTS + "/" + UUID.randomUUID()), Caller.SUPPORT, ana).andExpect(status().isNotFound());
    }
}
