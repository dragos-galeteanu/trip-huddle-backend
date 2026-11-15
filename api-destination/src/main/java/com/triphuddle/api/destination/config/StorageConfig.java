package com.triphuddle.api.destination.config;

import com.google.cloud.storage.Storage;
import com.triphuddle.common.config.BaseStorageConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * The Cloud Storage client for place photos, created only when a bucket is configured.
 */
@Configuration
public class StorageConfig implements BaseStorageConfig {

    /**
     * Registers the storage client for the photos bucket as a bean.
     *
     * @param properties the destination settings
     * @return the storage client
     * @throws IOException if the application default credentials cannot be read
     */
    @Bean
    @ConditionalOnExpression("!'${app.destination.photos.bucket:}'.isBlank()")
    public Storage storage(DestinationProperties properties) throws IOException {
        return BaseStorageConfig.super.storage(properties.photos());
    }
}
