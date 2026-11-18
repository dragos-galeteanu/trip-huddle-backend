package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.dto.ChatEvent;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.common.config.StompDestinations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatEventPublisherTest {

    final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    final ChatEventPublisher publisher = new ChatEventPublisher(template);
    final UUID ana = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void outside_a_transaction_each_user_gets_the_event_at_once() {
        publisher.publish(List.of(ana, bob), ChatEventType.TYPING, UUID.randomUUID(), null);

        then(template).should().convertAndSend(eq(StompDestinations.userTopic(ana)), any(ChatEvent.class));
        then(template).should().convertAndSend(eq(StompDestinations.userTopic(bob)), any(ChatEvent.class));
    }

    @Test
    void inside_a_transaction_events_wait_for_the_commit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(List.of(ana), ChatEventType.MESSAGE_CREATED, UUID.randomUUID(), null);
        publisher.publishToTopic("/topic/support.queue", ChatEventType.TICKET_UPDATED, UUID.randomUUID(), null);
        then(template).should(never()).convertAndSend(anyString(), any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        then(template).should().convertAndSend(eq(StompDestinations.userTopic(ana)), any(ChatEvent.class));
        then(template).should().convertAndSend(eq("/topic/support.queue"), any(ChatEvent.class));
    }

    @Test
    void nobody_to_tell_sends_nothing_and_a_broker_failure_is_swallowed() {
        publisher.publish(List.of(), ChatEventType.TYPING, UUID.randomUUID(), null);
        then(template).should(never()).convertAndSend(anyString(), any(Object.class));

        willThrow(new MessageDeliveryException("down")).given(template).convertAndSend(anyString(), any(Object.class));
        publisher.publish(List.of(ana, bob), ChatEventType.TYPING, UUID.randomUUID(), null);
        then(template).should().convertAndSend(eq(StompDestinations.userTopic(bob)), any(ChatEvent.class));
    }
}
