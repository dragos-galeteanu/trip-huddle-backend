package com.triphuddle.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Base Cloud Storage configuration class for building a module's storage client.
 */
public interface BaseStorageConfig {

    Logger log = LoggerFactory.getLogger(BaseStorageConfig.class);

    /**
     * Client authenticated with the application default credentials.
     *
     * @param properties the bucket settings
     * @return the storage client
     * @throws IOException if the application default credentials cannot be read
     */
    default Storage storage(StorageBucketProperties properties) throws IOException {
        // Signing URLs needs a service account: its key locally, or iam.serviceAccounts.signBlob on GCP.
        log.info("Files stored in bucket [bucket={}]", properties.bucket());
        return StorageOptions.newBuilder()
                .setProjectId(properties.projectId())
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
                .getService();
    }
}
