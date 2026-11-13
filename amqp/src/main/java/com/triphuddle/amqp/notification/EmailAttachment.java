package com.triphuddle.amqp.notification;

/**
 * A file attached to an email.
 *
 * @param filename the name the recipient sees
 * @param content the bytes of the file
 * @param contentType the MIME type of the file
 */
public record EmailAttachment(
        String filename,
        byte[] content,
        String contentType
) {
}
