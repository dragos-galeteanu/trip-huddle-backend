package com.triphuddle.api.chat.config;

import com.google.cloud.storage.Storage;
import com.triphuddle.common.config.BaseStorageConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * The Cloud Storage client for attachments, created only when a bucket is configured.
 */
@Configuration
public class StorageConfig implements BaseStorageConfig {

    /**
     * Registers the storage client for the attachments bucket as a bean.
     *
     * @param chatProperties the chat settings
     * @return the storage client
     * @throws IOException if the application default credentials cannot be read
     */
    @Bean
    @ConditionalOnExpression("!'${app.chat.attachments.bucket:}'.isBlank()")
    public Storage storage(ChatProperties chatProperties) throws IOException {
        return BaseStorageConfig.super.storage(chatProperties.attachments());
    }
}
