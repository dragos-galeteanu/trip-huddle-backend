package com.triphuddle.amqp.user;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId) {
}
