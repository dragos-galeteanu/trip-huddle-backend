package com.triphuddle.notification.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * A device a user registered for push notifications, with its Firebase Cloud Messaging token.
 */
@Document(collection = "device_tokens")
@Data
@NoArgsConstructor
public class DeviceToken {

    @Id
    private ObjectId id;

    // A token identifies one app install; if another account signs in on it, the token moves to them.
    @Indexed(unique = true)
    private String token;

    @Indexed
    private UUID userId;

    private String platform;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
