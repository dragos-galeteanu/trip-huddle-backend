package com.triphuddle.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Turns on Mongo auditing, without which the {@code createdAt} and {@code updatedAt} of device tokens stay null.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
