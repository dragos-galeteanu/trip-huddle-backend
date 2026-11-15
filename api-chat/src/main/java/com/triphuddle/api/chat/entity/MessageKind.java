package com.triphuddle.api.chat.entity;

/**
 * A {@code USER} message is written by a participant, a {@code SYSTEM} one records a change to the
 * conversation, and an {@code INTERNAL_NOTE} is only seen by the staff of a support chat.
 */
public enum MessageKind {
    USER,
    SYSTEM,
    INTERNAL_NOTE
}
