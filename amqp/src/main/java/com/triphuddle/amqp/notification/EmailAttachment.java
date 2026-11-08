package com.triphuddle.amqp.notification;

public record EmailAttachment(
        String filename,
        byte[] content,
        String contentType
) {
}
