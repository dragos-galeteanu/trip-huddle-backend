package com.triphuddle.amqp.group;

import java.util.UUID;

/**
 * Published when a group is updated.
 *
 * @param groupId the ID of the group
 * @param name the name of the group
 */
public record GroupUpdatedEvent(UUID groupId, String name) {
}
