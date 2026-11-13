package com.triphuddle.common.entity;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/**
 * Fields every Mongo document has: ID, audit timestamps and the soft-disable and soft-delete timestamps.
 */
@Getter
@Setter
public class BaseMongoDocument {

    @Id
    protected ObjectId id;

    @CreatedDate
    protected Instant createdAt;

    @LastModifiedDate
    protected Instant updatedAt;

    protected Instant disabledAt = null;

    protected Instant deletedAt = null;
}
