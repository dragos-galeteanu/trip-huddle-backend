package com.triphuddle.amqp.group;

import java.util.UUID;

/**
 * Published when a group is disabled.
 *
 * @param groupId the ID of the group
 * @param name the name of the group
 */
public record GroupDisabledEvent(UUID groupId, String name) {
}
