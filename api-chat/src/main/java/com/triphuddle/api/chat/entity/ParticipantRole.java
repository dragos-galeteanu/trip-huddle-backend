package com.triphuddle.api.chat.entity;

/**
 * Mirrors the member's role in the group; {@code ADMIN} may delete anyone's messages in a group chat.
 */
public enum ParticipantRole {
    ADMIN,
    MEMBER
}
