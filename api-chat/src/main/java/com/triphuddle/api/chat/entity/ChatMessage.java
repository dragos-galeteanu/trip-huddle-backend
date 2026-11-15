package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseMongoDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A message in a conversation. Stored in Mongo; deleting keeps the content for moderation but hides it.
 */
@Document(collection = "chat_messages")
@CompoundIndexes({
        // History is read newest first by ObjectId, which also serves as the pagination cursor.
        @CompoundIndex(name = "ix_conversation_id", def = "{'conversationId': 1, '_id': -1}"),
        // A client retrying a send with the same clientMessageId gets the first message back.
        @CompoundIndex(name = "uk_sender_client_message", def = "{'senderId': 1, 'clientMessageId': 1}", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChatMessage extends BaseMongoDocument {

    private UUID conversationId;

    private UUID senderId;

    private String clientMessageId;

    // Missing on messages stored before kinds existed, which are all USER messages.
    private MessageKind kind = MessageKind.USER;

    private String body;

    // Set on SYSTEM messages only.
    private SystemMessageEvent systemEvent;

    private ObjectId replyToMessageId;

    private List<MessageAttachment> attachments = new ArrayList<>();

    private List<MessageReaction> reactions = new ArrayList<>();

    private Instant editedAt;

    private UUID deletedByUserId;

    /**
     * The kind of the message, reading a missing kind as {@code USER}.
     *
     * @return the kind of the message
     */
    public MessageKind getKind() {
        return kind == null ? MessageKind.USER : kind;
    }

    /**
     * Whether only the staff of a support chat may see the message.
     *
     * @return true if the message is an internal note
     */
    public boolean isInternalNote() {
        return getKind() == MessageKind.INTERNAL_NOTE;
    }
}
