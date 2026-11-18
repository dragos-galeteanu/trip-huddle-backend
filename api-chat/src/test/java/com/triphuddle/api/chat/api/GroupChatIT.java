package com.triphuddle.api.chat.api;

import com.triphuddle.amqp.destination.ItineraryFinishedEvent;
import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.group.GroupMembershipDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipUpdatedEvent;
import com.triphuddle.amqp.group.GroupUpdatedEvent;
import com.triphuddle.api.chat.controller.TypingController;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.dto.TypingRequest;
import com.triphuddle.api.chat.messaging.ItineraryEventListener;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CHT-1, CHT-3 and CHT-4 for group chats, which follow api-group's events. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GroupChatIT extends ChatApiSupport {

    @Autowired TypingController typing;
    @Autowired ItineraryEventListener itineraryEvents;

    @Nested
    class Following_the_group {

        @Test
        void the_chat_takes_the_groups_name_and_its_members() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID groupId = group(ana, bob);
            UUID chat = conversationOfGroup(groupId);

            groupEvents.onGroupUpdated(new GroupUpdatedEvent(groupId, "Porto trip"));

            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, bob)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("GROUP"))
                    .andExpect(jsonPath("$.name").value("Porto trip"))
                    .andExpect(jsonPath("$.participants.length()").value(2));
            assertThat(broker.to(ana, ChatEventType.CONVERSATION_UPDATED)).isNotEmpty();
        }

        @Test
        void a_membership_that_arrives_before_its_group_still_finds_one_chat() {
            UUID groupId = UUID.randomUUID();
            UUID ana = user();

            groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), groupId, ana, "MEMBER"));
            groupEvents.onGroupCreated(new com.triphuddle.amqp.group.GroupCreatedEvent(groupId, "Late"));

            assertThat(jdbc.queryForObject("select count(*) from conversations where group_id = ?", Long.class,
                    groupId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select name from conversations where group_id = ?", String.class,
                    groupId)).isEqualTo("Late");
        }

        @Test
        void a_disabled_or_deleted_group_leaves_a_read_only_chat() throws Exception {
            UUID ana = user();
            UUID disabled = group(ana);
            UUID deleted = group(ana);

            groupEvents.onGroupDisabled(new GroupDisabledEvent(disabled, "x"));
            groupEvents.onGroupDisabled(new GroupDisabledEvent(disabled, "x"));
            groupEvents.onGroupDeleted(new GroupDeletedEvent(deleted, "x"));

            for (UUID groupId : List.of(disabled, deleted)) {
                UUID chat = conversationOfGroup(groupId);
                call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, ana).andExpect(jsonPath("$.isReadOnly").value(true));
                sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", UUID.randomUUID().toString(),
                        "body", "x")).andExpect(status().isUnprocessableEntity());
            }
        }

        @Test
        void a_group_admin_removes_anyones_message_and_loses_that_right_with_the_role() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID groupId = group(ana, bob);
            UUID chat = conversationOfGroup(groupId);
            String first = say(chat, bob, "Spam");
            String second = say(chat, bob, "More spam");

            call(delete(MESSAGES + "/" + first), Caller.CUSTOMER, ana).andExpect(status().isOk());

            groupEvents.onMembershipUpdated(new GroupMembershipUpdatedEvent(UUID.randomUUID(), groupId, ana, "MEMBER"));
            groupEvents.onMembershipUpdated(new GroupMembershipUpdatedEvent(UUID.randomUUID(), UUID.randomUUID(), ana,
                    "ADMIN"));
            call(delete(MESSAGES + "/" + second), Caller.CUSTOMER, ana).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Leaving_and_rejoining {

        @Test
        void a_member_who_left_keeps_the_history_up_to_their_leaving() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID groupId = group(ana, bob);
            UUID chat = conversationOfGroup(groupId);
            String before = say(chat, ana, "Before");
            Thread.sleep(5);

            groupEvents.onMembershipDisabled(new GroupMembershipDisabledEvent(UUID.randomUUID(), groupId, bob, "MEMBER"));
            Thread.sleep(5);
            say(chat, ana, "After");

            assertThat(history(chat, Caller.CUSTOMER, bob).findValuesAsText("id")).containsExactly(before);
            assertThat(broker.to(bob, ChatEventType.MESSAGE_CREATED)).hasSize(1);
            sendMessage(chat, Caller.CUSTOMER, bob, Map.of("clientMessageId", "x", "body", "Can I?"))
                    .andExpect(status().isForbidden());
            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, bob).andExpect(jsonPath("$.leftAt").exists());
        }

        @Test
        void rejoining_brings_the_whole_history_back() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID groupId = group(ana, bob);
            UUID chat = conversationOfGroup(groupId);
            groupEvents.onMembershipDeleted(new GroupMembershipDeletedEvent(UUID.randomUUID(), groupId, bob, "MEMBER"));
            groupEvents.onMembershipDeleted(new GroupMembershipDeletedEvent(UUID.randomUUID(), groupId, bob, "MEMBER"));
            Thread.sleep(5);
            say(chat, ana, "While you were away");

            groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), groupId, bob, "MEMBER"));

            assertThat(history(chat, Caller.CUSTOMER, bob)).hasSize(1);
            say(chat, bob, "Back");
        }

        @Test
        void leaving_a_group_nobody_knows_changes_nothing() {
            groupEvents.onMembershipDisabled(new GroupMembershipDisabledEvent(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), "MEMBER"));

            assertThat(broker.sent()).isEmpty();
        }
    }

    @Nested
    class Typing_and_itineraries {

        @Test
        void typing_reaches_the_other_active_members_only() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID cid = user();
            UUID groupId = group(ana, bob, cid);
            UUID chat = conversationOfGroup(groupId);
            groupEvents.onMembershipDisabled(new GroupMembershipDisabledEvent(UUID.randomUUID(), groupId, cid, "MEMBER"));
            broker.reset();

            typing.typing(chat, null, Caller.CUSTOMER.authentication(ana));
            typing.typing(chat, new TypingRequest(false), Caller.CUSTOMER.authentication(ana));

            assertThat(broker.to(bob, ChatEventType.TYPING)).hasSize(2);
            assertThat(broker.to(ana, ChatEventType.TYPING)).isEmpty();
            assertThat(broker.to(cid, ChatEventType.TYPING)).isEmpty();
        }

        @Test
        void typing_is_ignored_from_outsiders_and_in_read_only_chats() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID groupId = group(ana, bob);
            UUID chat = conversationOfGroup(groupId);
            groupEvents.onGroupDisabled(new GroupDisabledEvent(groupId, "x"));
            broker.reset();

            typing.typing(chat, null, Caller.CUSTOMER.authentication(user()));
            typing.typing(chat, null, null);
            typing.typing(chat, null, Caller.CUSTOMER.authentication(ana));

            assertThat(broker.sent()).isEmpty();
        }

        @Test
        void a_finished_itinerary_is_announced_to_its_travellers() {
            UUID ana = user();
            UUID bob = user();

            itineraryEvents.onFinished(new ItineraryFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), "Lisbon",
                    "READY", List.of(ana, bob), ana, LocalDate.now(), 3, "https://app.example/plans/1"));

            assertThat(broker.to(ana, ChatEventType.ITINERARY_FINISHED)).hasSize(1);
            assertThat(broker.to(bob, ChatEventType.ITINERARY_FINISHED)).hasSize(1);
        }
    }
}
