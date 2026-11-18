package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.MessageKind;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import com.triphuddle.api.chat.repository.ConversationParticipantRepository;
import com.triphuddle.api.chat.repository.ConversationRepository;
import com.triphuddle.testsupport.security.Caller;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatAccessTest {

    final ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    final ConversationRepository conversations = mock(ConversationRepository.class);
    final ChatAccess access = new ChatAccess(participants, messages, conversations);

    final UUID me = UUID.randomUUID();
    final UUID chatId = UUID.randomUUID();
    final Conversation chat = new Conversation();

    ConversationParticipant participant(ParticipantRole role, Instant leftAt) {
        chat.setId(chatId);
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(chat);
        participant.setUserId(me);
        participant.setRole(role);
        participant.setLeftAt(leftAt);
        given(participants.findByConversationIdAndUserId(chatId, me)).willReturn(Optional.of(participant));
        return participant;
    }

    ChatMessage message(MessageKind kind, Instant createdAt) {
        ChatMessage message = new ChatMessage();
        message.setId(new ObjectId());
        message.setConversationId(chatId);
        message.setSenderId(UUID.randomUUID());
        message.setKind(kind);
        message.setCreatedAt(createdAt);
        given(messages.findById(message.getId())).willReturn(Optional.of(message));
        return message;
    }

    @Test
    void support_staff_are_admins_and_support_whatever_the_authoritys_case() {
        assertThat(access.isSupportStaff(Caller.SUPPORT.authentication())).isTrue();
        assertThat(access.isSupportStaff(Caller.ADMIN.authentication())).isTrue();
        assertThat(access.isSupportStaff(new TestingAuthenticationToken("x", "y", "support"))).isTrue();
        assertThat(access.isSupportStaff(Caller.INSPECTOR.authentication())).isFalse();
        assertThat(access.isSupportStaff(null)).isFalse();
    }

    @Test
    void a_member_who_left_is_a_participant_but_no_longer_active() {
        participant(ParticipantRole.MEMBER, Instant.now());
        Authentication caller = Caller.CUSTOMER.authentication(me);

        assertThat(access.isParticipant(caller, chatId)).isTrue();
        assertThat(access.isActiveParticipant(caller, chatId)).isFalse();
        assertThat(access.isParticipant(caller, null)).isFalse();
        assertThat(access.isParticipant(Caller.ANONYMOUS.authentication(), chatId)).isFalse();
    }

    @Test
    void staff_keep_their_place_in_a_support_chat_but_write_only_while_they_have_the_role() {
        participant(ParticipantRole.STAFF, null);

        assertThat(access.isActiveParticipant(Caller.SUPPORT.authentication(me), chatId)).isTrue();
        assertThat(access.isActiveParticipant(Caller.CUSTOMER.authentication(me), chatId)).isFalse();
    }

    @Test
    void any_support_staff_reads_a_support_chat_but_no_other() {
        chat.setType(ConversationType.SUPPORT);
        given(conversations.findByIdAndDeletedAtIsNull(chatId)).willReturn(Optional.of(chat));
        ChatMessage message = message(MessageKind.USER, Instant.now());

        assertThat(access.canReadHistory(Caller.SUPPORT.authentication(), chatId)).isTrue();
        assertThat(access.canReadMessage(Caller.SUPPORT.authentication(), message.getId().toHexString())).isTrue();
        assertThat(access.canReadHistory(Caller.CUSTOMER.authentication(), chatId)).isFalse();
        chat.setType(ConversationType.GROUP);
        assertThat(access.canReadHistory(Caller.SUPPORT.authentication(), chatId)).isFalse();
    }

    @Test
    void a_reader_sees_messages_up_to_their_leaving_and_notes_only_as_staff() {
        Instant left = Instant.now();
        ConversationParticipant member = participant(ParticipantRole.MEMBER, left);

        assertThat(ChatAccess.isVisibleTo(message(MessageKind.USER, left.minusSeconds(1)), member)).isTrue();
        assertThat(ChatAccess.isVisibleTo(message(MessageKind.USER, left.plusSeconds(1)), member)).isFalse();
        assertThat(ChatAccess.isVisibleTo(message(MessageKind.USER, null), member)).isFalse();
        member.setLeftAt(null);
        assertThat(ChatAccess.isVisibleTo(message(MessageKind.INTERNAL_NOTE, left), member)).isFalse();
        member.setRole(ParticipantRole.STAFF);
        assertThat(ChatAccess.isVisibleTo(message(MessageKind.INTERNAL_NOTE, left), member)).isTrue();
    }

    @Test
    void message_checks_answer_no_for_ids_that_are_not_object_ids_or_not_found() {
        Authentication caller = Caller.CUSTOMER.authentication(me);

        assertThat(access.canReadMessage(caller, "nope")).isFalse();
        assertThat(access.canReadMessage(caller, null)).isFalse();
        assertThat(access.isActiveInMessageConversation(caller, new ObjectId().toHexString())).isFalse();
        assertThat(access.findSenderId(new ObjectId().toHexString())).isEmpty();
    }

    @Test
    void a_group_admin_is_an_active_admin_of_a_group_chat() {
        ConversationParticipant admin = participant(ParticipantRole.ADMIN, null);
        chat.setType(ConversationType.GROUP);
        String id = message(MessageKind.USER, Instant.now()).getId().toHexString();
        Authentication caller = Caller.CUSTOMER.authentication(me);

        assertThat(access.isGroupAdminOfMessageConversation(caller, id)).isTrue();
        chat.setType(ConversationType.PRIVATE);
        assertThat(access.isGroupAdminOfMessageConversation(caller, id)).isFalse();
        chat.setType(ConversationType.GROUP);
        admin.setLeftAt(Instant.now());
        assertThat(access.isGroupAdminOfMessageConversation(caller, id)).isFalse();
    }
}
