package com.triphuddle.common.config;

/**
 * A Cloud Storage bucket files are uploaded to through signed URLs; an empty bucket turns it off.
 *
 * @param projectId the Google Cloud project of the bucket
 * @param bucket the name of the bucket
 * @param maxSizeBytes the largest file accepted
 * @param uploadUrlTtlMinutes how long an upload URL works
 * @param downloadUrlTtlMinutes how long a download URL works
 */
public record StorageBucketProperties(String projectId, String bucket, long maxSizeBytes,
                                      long uploadUrlTtlMinutes, long downloadUrlTtlMinutes) {

    /**
     * Whether a bucket is configured.
     *
     * @return true if files can be uploaded
     */
    public boolean isEnabled() {
        return bucket != null && !bucket.isBlank();
    }
}
