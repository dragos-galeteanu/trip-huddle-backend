package com.triphuddle.api.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Turns on Mongo auditing for this service.
 *
 * <p>Without this, the {@code @CreatedDate} and {@code @LastModifiedDate} fields that
 * {@code BaseMongoDocument} declares are never populated — Spring Data applies them through an
 * auditing listener that is only registered when auditing is enabled, so {@code createdAt} and
 * {@code updatedAt} would silently stay null on every saved document.</p>
 *
 * <p>Separate from the JPA side, which needs no equivalent: Hibernate's
 * {@code @CreationTimestamp} and {@code @UpdateTimestamp} on {@code BaseJpaEntity} work without
 * any opt-in.</p>
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
