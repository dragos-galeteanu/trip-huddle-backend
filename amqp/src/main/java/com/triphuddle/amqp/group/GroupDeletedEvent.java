package com.triphuddle.amqp.group;

import java.util.UUID;

/**
 * Published when a group is deleted.
 *
 * @param groupId the ID of the group
 * @param name the name of the group
 */
public record GroupDeletedEvent(UUID groupId, String name) {
}
