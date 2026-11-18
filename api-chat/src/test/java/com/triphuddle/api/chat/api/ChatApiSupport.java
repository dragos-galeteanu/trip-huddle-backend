package com.triphuddle.api.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.group.GroupCreatedEvent;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.messaging.GroupEventListener;
import com.triphuddle.api.chat.messaging.UserEventListener;
import com.triphuddle.amqp.user.UserRegisteredEvent;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api-chat's integration tests share this: Postgres and Mongo in containers, and both RabbitMQ and the STOMP broker
 * replaced by recorders. Users and groups are seeded the way api-user and api-group announce them.
 */
@IntegrationTest
@Import({MongoContainerConfig.class, ChatApiSupport.RecordingBroker.class})
abstract class ChatApiSupport {

    static final String CONVERSATIONS = "/api/v1/chats/conversations";
    static final String MESSAGES = "/api/v1/chats/messages";
    static final String TICKETS = "/api/v1/chats/support-tickets";
    static final String REPORTS = "/api/v1/chats/reports";
    static final String BLOCKS = "/api/v1/chats/blocks";

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingBroker {
        @Bean
        @Primary
        RecordingMessagingTemplate recordingMessagingTemplate() {
            return new RecordingMessagingTemplate();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MongoTemplate mongo;
    @Autowired RecordingMessageProducer published;
    @Autowired RecordingMessagingTemplate broker;
    @Autowired UserEventListener userEvents;
    @Autowired GroupEventListener groupEvents;

    @BeforeEach
    void resetRecorders() {
        published.reset();
        broker.reset();
    }

    @AfterEach
    void deleteEverything() {
        jdbc.execute("truncate support_ticket_events, support_tickets, message_reports, user_blocks, "
                + "conversation_participants, conversations, chat_users cascade");
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), ChatMessage.class);
    }

    /** A user as api-user announced them. */
    UUID user() {
        UUID userId = UUID.randomUUID();
        userEvents.onUserRegistered(new UserRegisteredEvent(userId));
        return userId;
    }

    /** A group chat, as api-group announced the group and each membership; the first user is its admin. */
    UUID group(UUID admin, UUID... members) {
        UUID groupId = UUID.randomUUID();
        groupEvents.onGroupCreated(new GroupCreatedEvent(groupId, "Lisbon trip"));
        groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), groupId, admin, "ADMIN"));
        for (UUID member : members) {
            groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), groupId, member, "MEMBER"));
        }
        return groupId;
    }

    UUID conversationOfGroup(UUID groupId) {
        return jdbc.queryForObject("select id from conversations where group_id = ?", UUID.class, groupId);
    }

    UUID privateChat(UUID caller, UUID other) throws Exception {
        return idOf(body(call(post(CONVERSATIONS + "/private"), Caller.CUSTOMER, caller,
                Map.of("userId", other)).andExpect(status().isOk())));
    }

    /** Sends a message and returns its ID. */
    String say(UUID conversationId, UUID sender, String body) throws Exception {
        return say(conversationId, Caller.CUSTOMER, sender, body);
    }

    String say(UUID conversationId, Caller caller, UUID sender, String body) throws Exception {
        return body(sendMessage(conversationId, caller, sender, Map.of("clientMessageId", UUID.randomUUID().toString(),
                "body", body)).andExpect(status().isCreated())).get("id").asText();
    }

    ResultActions sendMessage(UUID conversationId, Caller caller, UUID sender, Object request) throws Exception {
        return call(post(CONVERSATIONS + "/" + conversationId + "/messages"), caller, sender, request);
    }

    JsonNode history(UUID conversationId, Caller caller, UUID reader) throws Exception {
        return body(mvc.perform(get(CONVERSATIONS + "/" + conversationId + "/messages").with(caller.request(reader)))
                .andExpect(status().isOk())).get("data");
    }

    ResultActions call(MockHttpServletRequestBuilder request, Caller caller, UUID userId, Object body)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request.with(caller.request(userId)));
    }

    ResultActions call(MockHttpServletRequestBuilder request, Caller caller, UUID userId) throws Exception {
        return call(request, caller, userId, null);
    }

    JsonNode body(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    /** The IDs of the items of a list, not of what they nest. */
    static java.util.List<String> ids(JsonNode items) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    static UUID idOf(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }
}
