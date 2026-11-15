package com.triphuddle.api.chat.entity;

/**
 * {@code ADMIN} and {@code MEMBER} mirror the member's role in a group; the customer of a support chat is a
 * {@code MEMBER} and the Support or Admin users helping them are {@code STAFF}.
 */
public enum ParticipantRole {
    ADMIN,
    MEMBER,
    STAFF
}
