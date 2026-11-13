package com.triphuddle.api.group.entity;

/**
 * Where an invitation stands: {@code PENDING} until the invitee accepts or rejects it,
 * or the group's side cancels it.
 */
public enum GroupInvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED
}
