package com.triphuddle.api.chat.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Where and how to upload an attachment; send {@code objectPath} with the message afterwards.
 *
 * @param uploadUrl the signed URL to upload the file to
 * @param method the HTTP method to upload with
 * @param requiredHeaders the headers the upload request must carry, exactly as given
 * @param objectPath the path of the file in the bucket, to attach to the message
 * @param expiresAt when the upload URL stops working
 */
public record UploadUrlResponse(
        String uploadUrl,
        String method,
        Map<String, String> requiredHeaders,
        String objectPath,
        Instant expiresAt
) {
}
