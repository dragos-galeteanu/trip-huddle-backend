package com.triphuddle.api.chat.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle of a support ticket; {@code OPEN} means nobody has claimed it yet.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_CUSTOMER,
    RESOLVED,
    CLOSED;

    /**
     * The statuses that count towards a customer's limit of open tickets.
     */
    public static final Set<TicketStatus> ONGOING = EnumSet.of(OPEN, IN_PROGRESS, WAITING_ON_CUSTOMER);

    // OPEN only leaves for IN_PROGRESS by being claimed; reopening goes to OPEN or IN_PROGRESS by the assignee.
    private static final Map<TicketStatus, Set<TicketStatus>> STAFF_TRANSITIONS = Map.of(
            OPEN, EnumSet.of(CLOSED),
            IN_PROGRESS, EnumSet.of(WAITING_ON_CUSTOMER, RESOLVED, CLOSED),
            WAITING_ON_CUSTOMER, EnumSet.of(IN_PROGRESS, RESOLVED, CLOSED),
            RESOLVED, EnumSet.of(OPEN, IN_PROGRESS, CLOSED),
            CLOSED, EnumSet.of(OPEN, IN_PROGRESS)
    );

    /**
     * Whether staff may move a ticket from this status to another.
     *
     * @param target the status to move to
     * @return true if the move is allowed
     */
    public boolean canStaffMoveTo(TicketStatus target) {
        return STAFF_TRANSITIONS.get(this).contains(target);
    }
}
