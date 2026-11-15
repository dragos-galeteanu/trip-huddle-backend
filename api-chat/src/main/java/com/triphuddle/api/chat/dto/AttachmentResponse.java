package com.triphuddle.api.chat.dto;

/**
 * A file attached to a message, with a short-lived URL to download it.
 *
 * @param objectPath the path of the file in the bucket
 * @param fileName the name the sender gave the file
 * @param contentType the MIME type of the file
 * @param sizeBytes the size of the file in bytes
 * @param downloadUrl a signed URL to download the file, or null if attachments are not configured
 */
public record AttachmentResponse(
        String objectPath,
        String fileName,
        String contentType,
        long sizeBytes,
        String downloadUrl
) {
}
