package com.triphuddle.api.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.group.GroupMembershipDisabledEvent;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHT-2, CHT-4 and CHT-6: system messages are nobody's to change, the queue ranks every priority, and someone who
 * left a group chat counts only what was said before they left.
 */
class ChatRulesIT extends ChatApiSupport {

    @Test
    void aSystemMessageCantBeEditedDeletedOrReactedTo() throws Exception {
        UUID customer = user();
        UUID agent = user();
        JsonNode ticket = openTicket(customer, "PAYMENT");
        call(post(TICKETS + "/" + ticket.get("id").asText() + "/claim"), Caller.SUPPORT, agent)
                .andExpect(status().isOk());
        UUID chat = UUID.fromString(ticket.get("conversationId").asText());
        // Newest first: the agent's claim posted the latest system message, so it is theirs.
        String system = null;
        for (JsonNode message : history(chat, Caller.CUSTOMER, customer)) {
            if (system == null && "SYSTEM".equals(message.get("kind").asText())
                    && agent.toString().equals(message.get("senderId").asText())) {
                system = message.get("id").asText();
            }
        }
        assertThat(system).as("claiming posts a system message").isNotNull();

        call(patch(MESSAGES + "/" + system), Caller.SUPPORT, agent, Map.of("body", "Rewritten"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A system message cannot be edited"));
        call(delete(MESSAGES + "/" + system), Caller.SUPPORT, agent).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A system message cannot be deleted"));
        call(put(MESSAGES + "/{id}/reactions/{emoji}", system, "👍"), Caller.SUPPORT, agent)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You cannot react to a system message"));
        // System messages are posted only in support chats so far, and nothing there is reported.
        call(post(MESSAGES + "/" + system + "/reports"), Caller.CUSTOMER, customer, Map.of("reason", "Spam"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Messages in support chats cannot be reported"));
    }

    @Test
    void onlyTheSenderEditsAMessage() throws Exception {
        UUID ana = user();
        UUID ben = user();
        UUID groupId = group(ana, ben);
        String message = say(conversationOfGroup(groupId), ana, "Hello");

        call(patch(MESSAGES + "/" + message), Caller.CUSTOMER, ben, Map.of("body", "Mine now"))
                .andExpect(status().isForbidden());
        // The policy finds no sender for an ID that can't exist, so it refuses before the service looks.
        call(patch(MESSAGES + "/not-an-id"), Caller.CUSTOMER, ana, Map.of("body", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theQueueRanksUrgentThenHighThenNormalThenLow() throws Exception {
        UUID agent = user();
        JsonNode low = openTicket(user(), "BUG");
        JsonNode normal = openTicket(user(), "BUG");
        JsonNode urgent = openTicket(user(), "BUG");
        JsonNode high = openTicket(user(), "BUG");
        prioritize(low, agent, "LOW");
        prioritize(urgent, agent, "URGENT");
        prioritize(high, agent, "HIGH");
        prioritize(normal, agent, "NORMAL");

        // One ticket a page, so each page's cursor carries the rank the next page continues from.
        java.util.List<String> walked = new java.util.ArrayList<>();
        String cursor = null;
        do {
            var request = get(TICKETS).param("limit", "1");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = body(call(request, Caller.SUPPORT, agent).andExpect(status().isOk()));
            walked.addAll(ids(page.get("data")));
            cursor = page.get("hasMore").asBoolean() ? page.get("nextCursor").asText() : null;
        } while (cursor != null);

        assertThat(walked).containsExactly(urgent.get("id").asText(), high.get("id").asText(),
                normal.get("id").asText(), low.get("id").asText());
    }

    @Test
    void aTicketClosedBeforeAnyoneClaimedItReopensIntoTheQueue() throws Exception {
        UUID agent = user();
        JsonNode ticket = openTicket(user(), "BUG");
        String status = TICKETS + "/" + ticket.get("id").asText() + "/status";
        call(patch(status), Caller.SUPPORT, agent, Map.of("status", "CLOSED")).andExpect(status().isOk());

        call(patch(status), Caller.SUPPORT, agent, Map.of("status", "IN_PROGRESS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    void aMemberWhoLeftCountsAsUnreadOnlyWhatWasSaidBeforeTheyLeft() throws Exception {
        UUID ana = user();
        UUID bob = user();
        UUID groupId = group(ana, bob);
        UUID chat = conversationOfGroup(groupId);
        String first = say(chat, ana, "One");
        say(chat, ana, "Two");
        Thread.sleep(5);
        groupEvents.onMembershipDisabled(new GroupMembershipDisabledEvent(UUID.randomUUID(), groupId, bob, "MEMBER"));
        Thread.sleep(5);
        say(chat, ana, "Three, after Bob left");

        call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob, Map.of("messageId", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.lastMessagePreview").doesNotExist())
                .andExpect(jsonPath("$.isReadOnly").value(true));
        call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob, Map.of("messageId", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMessageNeedsABodyOrAnAttachment() throws Exception {
        UUID ana = user();
        UUID chat = privateChat(ana, user());

        sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", UUID.randomUUID().toString(), "body", "   "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("needs a body")));
    }

    private JsonNode openTicket(UUID customer, String category) throws Exception {
        return body(call(post(TICKETS), Caller.CUSTOMER, customer, SupportTicketIT.ticketRequest(category))
                .andExpect(status().isCreated()));
    }

    private void prioritize(JsonNode ticket, UUID agent, String priority) throws Exception {
        call(patch(TICKETS + "/" + ticket.get("id").asText()), Caller.SUPPORT, agent, Map.of("priority", priority))
                .andExpect(status().isOk());
    }
}
