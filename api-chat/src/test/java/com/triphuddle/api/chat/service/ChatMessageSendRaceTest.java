package com.triphuddle.api.chat.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.api.chat.dto.MessageResponse;
import com.triphuddle.api.chat.dto.SendMessageRequest;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.mapper.ChatMessageMapper;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Two sends of the same message racing: the one that loses the unique index answers with the winner's message.
 */
class ChatMessageSendRaceTest {

    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ConversationService conversations = mock(ConversationService.class);
    private final ChatMessageMapper mapper = mock(ChatMessageMapper.class);
    private final ChatEventPublisher chatEvents = mock(ChatEventPublisher.class);

    private final ChatMessageService service = new ChatMessageService(messages, mock(MongoTemplate.class),
            conversations, mock(AttachmentService.class), mock(PushNotificationService.class), chatEvents, mapper,
            mock(RabbitMQMessageProducer.class), mock(ApplicationEventPublisher.class));

    private final UUID chat = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();
    private final SendMessageRequest request = new SendMessageRequest("c-1", "Hello", null, false,
            new java.util.ArrayList<>());

    ChatMessageSendRaceTest() {
        Conversation conversation = new Conversation();
        conversation.setId(chat);
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUserId(sender);
        participant.setRole(ParticipantRole.MEMBER);
        given(conversations.requireActiveParticipant(chat, sender)).willReturn(participant);
        given(messages.save(any(ChatMessage.class))).willThrow(new DuplicateKeyException("sender_client_message_uq"));
    }

    @Test
    void theLoserOfTheRaceAnswersWithTheWinnersMessageAndAnnouncesNothing() {
        ChatMessage winner = new ChatMessage();
        winner.setConversationId(chat);
        winner.setClientMessageId("c-1");
        MessageResponse response = mock(MessageResponse.class);
        given(messages.findBySenderIdAndClientMessageId(sender, "c-1")).willReturn(Optional.empty(),
                Optional.of(winner));
        given(mapper.toResponse(winner)).willReturn(response);

        assertThat(service.send(chat, sender, request)).isSameAs(response);
        then(chatEvents).shouldHaveNoInteractions();
    }

    @Test
    void aDuplicateWithNoWinnerToFindIsRethrown() {
        given(messages.findBySenderIdAndClientMessageId(sender, "c-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(chat, sender, request)).isInstanceOf(DuplicateKeyException.class);
    }
}
