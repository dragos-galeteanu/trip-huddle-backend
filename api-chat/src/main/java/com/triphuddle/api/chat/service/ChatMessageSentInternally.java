package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.ParticipantRole;

/**
 * Raised in-process, within the sending transaction, after a participant's message is saved.
 *
 * @param message the saved message
 * @param conversationType the type of the conversation
 * @param senderRole the sender's role in the conversation
 */
public record ChatMessageSentInternally(ChatMessage message, ConversationType conversationType,
                                        ParticipantRole senderRole) {
}
