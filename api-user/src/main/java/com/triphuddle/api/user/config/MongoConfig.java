package com.triphuddle.api.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Turns on Mongo auditing, without which {@code createdAt} and {@code updatedAt} of
 * {@code BaseMongoDocument} stay null.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
