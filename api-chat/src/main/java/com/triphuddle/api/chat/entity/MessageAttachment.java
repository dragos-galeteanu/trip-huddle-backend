package com.triphuddle.api.chat.entity;

/**
 * A file attached to a message, stored in the attachments bucket under {@code objectPath}.
 *
 * @param objectPath the path of the object in the bucket
 * @param fileName the name the sender gave the file
 * @param contentType the MIME type of the file
 * @param sizeBytes the size of the file in bytes
 */
public record MessageAttachment(String objectPath, String fileName, String contentType, long sizeBytes) {
}
