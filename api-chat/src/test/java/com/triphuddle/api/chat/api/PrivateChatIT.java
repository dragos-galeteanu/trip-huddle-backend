package com.triphuddle.api.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.chat.ChatMessageDeletedContract;
import com.triphuddle.amqp.chat.ChatMessageDeletedEvent;
import com.triphuddle.amqp.chat.ChatMessageEditedContract;
import com.triphuddle.amqp.chat.ChatMessageSentContract;
import com.triphuddle.amqp.chat.ChatMessageEditedEvent;
import com.triphuddle.amqp.chat.ChatMessageSentEvent;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CHT-1, CHT-2, CHT-3 and CHT-8 for private chats, over HTTP. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PrivateChatIT extends ChatApiSupport {

    @Nested
    class Opening {

        @Test
        void a_pair_of_users_shares_one_private_chat_whoever_opens_it() throws Exception {
            UUID ana = user();
            UUID bob = user();

            UUID first = privateChat(ana, bob);
            UUID again = privateChat(bob, ana);

            assertThat(again).isEqualTo(first);
            assertThat(broker.to(ana, ChatEventType.CONVERSATION_UPDATED)).hasSize(1);
            call(get(CONVERSATIONS + "/" + first), Caller.CUSTOMER, bob)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("PRIVATE"))
                    .andExpect(jsonPath("$.participants.length()").value(2));
        }

        @Test
        void nobody_opens_a_chat_with_themselves_an_unknown_user_or_someone_who_blocked_them() throws Exception {
            UUID ana = user();
            UUID bob = user();
            call(post(BLOCKS), Caller.CUSTOMER, bob, Map.of("userId", ana)).andExpect(status().isCreated());

            call(post(CONVERSATIONS + "/private"), Caller.CUSTOMER, ana, Map.of("userId", ana))
                    .andExpect(status().isUnprocessableEntity());
            call(post(CONVERSATIONS + "/private"), Caller.CUSTOMER, ana, Map.of("userId", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
            call(post(CONVERSATIONS + "/private"), Caller.CUSTOMER, ana, Map.of("userId", bob))
                    .andExpect(status().isForbidden());
            call(post(CONVERSATIONS + "/private"), Caller.ANONYMOUS, ana, Map.of("userId", bob))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void an_outsider_cannot_see_the_chat() throws Exception {
            UUID chat = privateChat(user(), user());

            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, user()).andExpect(status().isForbidden());
            call(get(CONVERSATIONS + "/" + chat + "/messages"), Caller.CUSTOMER, user())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Sending {

        @Test
        void a_message_reaches_both_users_in_real_time_and_the_other_by_push() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            broker.reset();

            String id = say(chat, ana, "  Hi Bob  ");

            assertThat(broker.to(ana, ChatEventType.MESSAGE_CREATED)).hasSize(1);
            assertThat(broker.to(bob, ChatEventType.MESSAGE_CREATED)).hasSize(1);
            published.assertPublished(new ChatMessageSentEvent(id, chat, ana), ChatMessageSentContract.CHAT_MESSAGE_SENT_QUEUE_NAME);
            SendPushRequest push = published.published().stream().map(p -> p.payload())
                    .filter(SendPushRequest.class::isInstance).map(SendPushRequest.class::cast).findFirst().orElseThrow();
            assertThat(push.recipientUserIds()).containsExactly(bob);
            assertThat(push.title()).isEqualTo("New message");
            assertThat(push.body()).isEqualTo("Hi Bob");
            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, bob)
                    .andExpect(jsonPath("$.lastMessageId").value(id))
                    .andExpect(jsonPath("$.lastMessagePreview").value("Hi Bob"))
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }

        @Test
        void sending_again_with_the_same_client_message_id_returns_the_first_message() throws Exception {
            UUID ana = user();
            UUID chat = privateChat(ana, user());
            Map<String, String> request = Map.of("clientMessageId", "client-1", "body", "Once");

            String first = body(sendMessage(chat, Caller.CUSTOMER, ana, request).andExpect(status().isCreated()))
                    .get("id").asText();
            String second = body(sendMessage(chat, Caller.CUSTOMER, ana, request).andExpect(status().isCreated()))
                    .get("id").asText();

            assertThat(second).isEqualTo(first);
            assertThat(history(chat, Caller.CUSTOMER, ana)).hasSize(1);
            published.assertPublishedCount(ChatMessageSentEvent.class, 1);
        }

        @Test
        void a_client_message_id_cannot_be_reused_in_another_chat() throws Exception {
            UUID ana = user();
            Map<String, String> request = Map.of("clientMessageId", "client-1", "body", "Once");
            sendMessage(privateChat(ana, user()), Caller.CUSTOMER, ana, request).andExpect(status().isCreated());

            sendMessage(privateChat(ana, user()), Caller.CUSTOMER, ana, request)
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void a_message_has_up_to_4000_characters_and_10_attachments_and_is_never_empty() throws Exception {
            UUID ana = user();
            UUID chat = privateChat(ana, user());
            List<Map<String, String>> eleven = IntStream.range(0, 11)
                    .mapToObj(i -> Map.of("objectPath", "chats/x/" + i, "fileName", i + ".png")).toList();

            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "a", "body", "x".repeat(4001)))
                    .andExpect(status().isBadRequest());
            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "b", "body", "x", "attachments", eleven))
                    .andExpect(status().isBadRequest());
            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "c", "body", "   "))
                    .andExpect(status().isUnprocessableEntity());
            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "d", "body", "x".repeat(4000)))
                    .andExpect(status().isCreated());
        }

        @Test
        void attachments_are_refused_while_no_bucket_is_configured() throws Exception {
            UUID ana = user();
            UUID chat = privateChat(ana, user());

            call(post(CONVERSATIONS + "/" + chat + "/attachments/upload-url"), Caller.CUSTOMER, ana,
                    Map.of("fileName", "a.png", "contentType", "image/png", "sizeBytes", 10))
                    .andExpect(status().isUnprocessableEntity());
            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "a", "attachments",
                    List.of(Map.of("objectPath", "chats/" + chat + "/" + ana + "/x", "fileName", "a.png"))))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void a_reply_points_at_a_message_of_the_same_chat() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            String question = say(chat, bob, "Lunch?");
            String elsewhere = say(privateChat(ana, user()), ana, "Other chat");

            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "r1", "body", "Yes",
                    "replyToMessageId", question))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.replyToMessageId").value(question));
            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "r2", "body", "Yes",
                    "replyToMessageId", elsewhere))
                    .andExpect(status().isNotFound());
        }

        @Test
        void a_broker_failure_does_not_fail_the_send() throws Exception {
            UUID ana = user();
            UUID chat = privateChat(ana, user());
            broker.failing(true);

            say(chat, ana, "Still saved");

            broker.failing(false);
            assertThat(history(chat, Caller.CUSTOMER, ana)).hasSize(1);
        }
    }

    @Nested
    class Blocks_and_inactive_users {

        @Test
        void a_block_stops_the_chat_both_ways_until_it_is_lifted() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            call(post(BLOCKS), Caller.CUSTOMER, ana, Map.of("userId", bob)).andExpect(status().isCreated());

            sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", "a", "body", "x"))
                    .andExpect(status().isForbidden());
            sendMessage(chat, Caller.CUSTOMER, bob, Map.of("clientMessageId", "b", "body", "x"))
                    .andExpect(status().isForbidden());

            call(delete(BLOCKS + "/" + bob), Caller.CUSTOMER, ana).andExpect(status().isNoContent());
            say(chat, bob, "Friends again");
        }

        @Test
        void a_disabled_or_deleted_user_cannot_send_nor_be_written_to() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID cid = user();
            UUID withBob = privateChat(ana, bob);
            UUID withCid = privateChat(ana, cid);
            userEvents.onUserDisabled(new UserDisabledEvent(bob));
            userEvents.onUserDeleted(new UserDeletedEvent(cid));

            sendMessage(withBob, Caller.CUSTOMER, bob, Map.of("clientMessageId", "a", "body", "x"))
                    .andExpect(status().isUnprocessableEntity());
            sendMessage(withBob, Caller.CUSTOMER, ana, Map.of("clientMessageId", "b", "body", "x"))
                    .andExpect(status().isUnprocessableEntity());
            sendMessage(withCid, Caller.CUSTOMER, ana, Map.of("clientMessageId", "c", "body", "x"))
                    .andExpect(status().isUnprocessableEntity());
            call(post(CONVERSATIONS + "/private"), Caller.CUSTOMER, ana, Map.of("userId", bob))
                    .andExpect(status().isNotFound());
        }

        @Test
        void a_disabled_user_announced_twice_or_before_registering_is_still_disabled() throws Exception {
            UUID ghost = UUID.randomUUID();
            userEvents.onUserDisabled(new UserDisabledEvent(ghost));
            userEvents.onUserDisabled(new UserDisabledEvent(ghost));
            userEvents.onUserDeleted(new UserDeletedEvent(ghost));
            userEvents.onUserDeleted(new UserDeletedEvent(ghost));
            userEvents.onUserRegistered(new com.triphuddle.amqp.user.UserRegisteredEvent(ghost));

            call(post(CONVERSATIONS + "/private"), Caller.CUSTOMER, user(), Map.of("userId", ghost))
                    .andExpect(status().isNotFound());
        }

        @Test
        void blocks_are_listed_lifted_once_and_never_aimed_at_oneself_or_nobody() throws Exception {
            UUID ana = user();
            UUID bob = user();
            call(post(BLOCKS), Caller.CUSTOMER, ana, Map.of("userId", bob))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.blockedUserId").value(bob.toString()))
                    .andExpect(jsonPath("$.createdAt").exists());
            call(post(BLOCKS), Caller.CUSTOMER, ana, Map.of("userId", bob)).andExpect(status().isCreated());

            call(get(BLOCKS + "?page=1&pageSize=10"), Caller.CUSTOMER, ana)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
            call(post(BLOCKS), Caller.CUSTOMER, ana, Map.of("userId", ana)).andExpect(status().isUnprocessableEntity());
            call(post(BLOCKS), Caller.CUSTOMER, ana, Map.of("userId", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
            call(delete(BLOCKS + "/" + bob), Caller.CUSTOMER, ana).andExpect(status().isNoContent());
            call(delete(BLOCKS + "/" + bob), Caller.CUSTOMER, ana).andExpect(status().isNotFound());
            call(get(BLOCKS + "?page=1&pageSize=10"), Caller.CUSTOMER, ana).andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    class Editing_deleting_and_reacting {

        @Test
        void only_the_sender_edits_and_everyone_hears_of_it() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            String id = say(chat, ana, "Helo");

            call(patch(MESSAGES + "/" + id), Caller.CUSTOMER, bob, Map.of("body", "Hacked"))
                    .andExpect(status().isForbidden());
            call(patch(MESSAGES + "/" + id), Caller.CUSTOMER, ana, Map.of("body", "Hello"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("Hello"))
                    .andExpect(jsonPath("$.isEdited").value(true));

            assertThat(broker.to(bob, ChatEventType.MESSAGE_UPDATED)).hasSize(1);
            published.assertPublished(new ChatMessageEditedEvent(id, chat, ana), ChatMessageEditedContract.CHAT_MESSAGE_EDITED_QUEUE_NAME);
            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, bob)
                    .andExpect(jsonPath("$.lastMessagePreview").value("Hello"));
        }

        @Test
        void a_deleted_message_keeps_its_place_but_not_its_content() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            String id = say(chat, ana, "Oops");

            call(delete(MESSAGES + "/" + id), Caller.CUSTOMER, bob).andExpect(status().isForbidden());
            call(delete(MESSAGES + "/" + id), Caller.CUSTOMER, ana)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isDeleted").value(true))
                    .andExpect(jsonPath("$.body").doesNotExist());

            JsonNode message = history(chat, Caller.CUSTOMER, bob).get(0);
            assertThat(message.get("isDeleted").asBoolean()).isTrue();
            assertThat(message.get("body").isNull()).isTrue();
            published.assertPublished(new ChatMessageDeletedEvent(id, chat, ana), ChatMessageDeletedContract.CHAT_MESSAGE_DELETED_QUEUE_NAME);
            call(get(CONVERSATIONS + "/" + chat), Caller.CUSTOMER, bob)
                    .andExpect(jsonPath("$.lastMessagePreview").doesNotExist());
            call(delete(MESSAGES + "/" + id), Caller.CUSTOMER, ana).andExpect(status().isNotFound());
            call(patch(MESSAGES + "/" + id), Caller.CUSTOMER, ana, Map.of("body", "Back"))
                    .andExpect(status().isUnprocessableEntity());
            call(put(MESSAGES + "/{id}/reactions/{emoji}", id, "👍"), Caller.CUSTOMER, bob)
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void reactions_are_grouped_by_emoji_and_counted_once_per_user() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            String id = say(chat, ana, "Tickets booked");

            call(put(MESSAGES + "/{id}/reactions/{emoji}", id, "🎉"), Caller.CUSTOMER, bob).andExpect(status().isOk());
            call(put(MESSAGES + "/{id}/reactions/{emoji}", id, "🎉"), Caller.CUSTOMER, bob).andExpect(status().isOk());
            call(put(MESSAGES + "/{id}/reactions/{emoji}", id, "🎉"), Caller.CUSTOMER, ana)
                    .andExpect(jsonPath("$.reactions[0].emoji").value("🎉"))
                    .andExpect(jsonPath("$.reactions[0].count").value(2));
            call(delete(MESSAGES + "/{id}/reactions/{emoji}", id, "🎉"), Caller.CUSTOMER, bob)
                    .andExpect(jsonPath("$.reactions[0].count").value(1));
            call(put(MESSAGES + "/{id}/reactions/{emoji}", id, "x".repeat(33)), Caller.CUSTOMER, bob)
                    .andExpect(status().isUnprocessableEntity());
            call(put(MESSAGES + "/{id}/reactions/{emoji}", id, "🎉"), Caller.CUSTOMER, user())
                    .andExpect(status().isForbidden());

            assertThat(broker.to(ana, ChatEventType.REACTIONS_UPDATED)).hasSize(4);
        }

        @Test
        void a_message_is_read_by_its_chat_only() throws Exception {
            UUID ana = user();
            UUID chat = privateChat(ana, user());
            String id = say(chat, ana, "Hi");

            call(get(MESSAGES + "/" + id), Caller.CUSTOMER, ana)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("Hi"));
            call(get(MESSAGES + "/" + id), Caller.CUSTOMER, user()).andExpect(status().isForbidden());
            call(get(MESSAGES + "/not-an-id"), Caller.CUSTOMER, ana).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Reading {

        @Test
        void marking_read_clears_the_unread_count_and_tells_the_readers_devices() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);
            String first = say(chat, ana, "One");
            String second = say(chat, ana, "Two");
            say(chat, bob, "Mine do not count");

            call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob, Map.of("messageId", first))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1))
                    .andExpect(jsonPath("$.lastReadMessageId").value(first));
            call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob, Map.of("messageId", second))
                    .andExpect(jsonPath("$.unreadCount").value(0));
            // Going back in time changes nothing.
            call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob, Map.of("messageId", first))
                    .andExpect(jsonPath("$.lastReadMessageId").value(second));

            assertThat(broker.to(bob, ChatEventType.READ_RECEIPT)).hasSize(2);
            assertThat(broker.to(ana, ChatEventType.READ_RECEIPT)).hasSize(2);
            call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob, Map.of("messageId", "nope"))
                    .andExpect(status().isNotFound());
            call(post(CONVERSATIONS + "/" + chat + "/read"), Caller.CUSTOMER, bob,
                    Map.of("messageId", say(privateChat(bob, user()), bob, "elsewhere")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void a_muted_chat_sends_no_push_until_the_mute_ends() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID chat = privateChat(ana, bob);

            call(put(CONVERSATIONS + "/" + chat + "/mute"), Caller.CUSTOMER, bob, Map.of("isMuted", true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isMuted").value(true));
            say(chat, ana, "Psst");
            published.assertPublishedCount(SendPushRequest.class, 0);

            Instant until = Instant.now().plus(1, ChronoUnit.DAYS);
            call(put(CONVERSATIONS + "/" + chat + "/mute"), Caller.CUSTOMER, bob,
                    Map.of("isMuted", false, "mutedUntil", until))
                    .andExpect(jsonPath("$.isMuted").value(false))
                    .andExpect(jsonPath("$.mutedUntil").doesNotExist());
            say(chat, ana, "Now?");
            published.assertPublishedCount(SendPushRequest.class, 1);
        }

        @Test
        void a_blocked_sender_does_not_buzz_the_blocker() throws Exception {
            UUID ana = user();
            UUID bob = user();
            UUID cid = user();
            UUID groupChat = conversationOfGroup(group(ana, bob, cid));
            call(post(BLOCKS), Caller.CUSTOMER, bob, Map.of("userId", ana)).andExpect(status().isCreated());

            say(groupChat, ana, "Hello all");

            SendPushRequest push = (SendPushRequest) published.published().stream().map(p -> p.payload())
                    .filter(SendPushRequest.class::isInstance).findFirst().orElseThrow();
            assertThat(push.recipientUserIds()).containsExactly(cid);
            assertThat(push.title()).isEqualTo("Lisbon trip");
        }

        @Test
        void history_pages_back_by_cursor_and_catches_up_after_a_reconnect() throws Exception {
            UUID ana = user();
            UUID chat = privateChat(ana, user());
            List<String> ids = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ids.add(say(chat, ana, "m" + i));
            }

            JsonNode page = body(call(get(CONVERSATIONS + "/" + chat + "/messages?limit=2"), Caller.CUSTOMER, ana));
            assertThat(page.get("hasMore").asBoolean()).isTrue();
            assertThat(page.get("data").findValuesAsText("id")).containsExactly(ids.get(4), ids.get(3));
            JsonNode older = body(call(get(CONVERSATIONS + "/" + chat + "/messages?limit=10&before="
                    + page.get("nextCursor").asText()), Caller.CUSTOMER, ana));
            List<String> rest = new java.util.ArrayList<>(ids.subList(0, 3));
            Collections.reverse(rest);
            assertThat(older.get("data").findValuesAsText("id")).containsExactlyElementsOf(rest);
            assertThat(older.get("hasMore").asBoolean()).isFalse();

            JsonNode newer = body(call(get(CONVERSATIONS + "/" + chat + "/messages?after=" + ids.get(2)),
                    Caller.CUSTOMER, ana));
            assertThat(newer.get("data").findValuesAsText("id")).containsExactly(ids.get(3), ids.get(4));

            call(get(CONVERSATIONS + "/" + chat + "/messages?after=" + ids.get(1) + "&before=" + ids.get(3)),
                    Caller.CUSTOMER, ana).andExpect(status().isBadRequest());
            call(get(CONVERSATIONS + "/" + chat + "/messages?before=bad"), Caller.CUSTOMER, ana)
                    .andExpect(status().isBadRequest());
        }

        @Test
        void the_list_shows_the_most_recent_chats_first_and_pages_by_cursor() throws Exception {
            UUID ana = user();
            UUID older = privateChat(ana, user());
            UUID newer = privateChat(ana, user());
            UUID group = conversationOfGroup(group(user(), ana));
            say(older, ana, "bump");

            JsonNode first = body(call(get(CONVERSATIONS + "?limit=2"), Caller.CUSTOMER, ana));
            assertThat(first.get("data").findValuesAsText("id")).first().isEqualTo(older.toString());
            assertThat(first.get("hasMore").asBoolean()).isTrue();
            JsonNode second = body(call(get(CONVERSATIONS + "?limit=2&cursor=" + first.get("nextCursor").asText()),
                    Caller.CUSTOMER, ana));
            assertThat(second.get("data")).hasSize(1);
            assertThat(second.get("hasMore").asBoolean()).isFalse();

            JsonNode groups = body(call(get(CONVERSATIONS + "?type=GROUP"), Caller.CUSTOMER, ana));
            assertThat(groups.get("data").findValuesAsText("id")).containsExactly(group.toString());
            assertThat(newer).isNotNull();
            call(get(CONVERSATIONS + "?cursor=garbage"), Caller.CUSTOMER, ana).andExpect(status().isBadRequest());
            call(get(CONVERSATIONS), Caller.ANONYMOUS, ana).andExpect(status().isUnauthorized());
        }
    }
}
