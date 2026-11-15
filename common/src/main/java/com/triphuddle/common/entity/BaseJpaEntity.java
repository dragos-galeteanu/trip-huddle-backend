package com.triphuddle.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Columns every JPA entity has: ID, audit timestamps and the soft-disable and soft-delete timestamps.
 */
@Getter
@Setter
@MappedSuperclass
public class BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    // Never rewritten: an update made before the entity is reloaded would otherwise write back a stale value.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    protected Instant updatedAt;

    @Column
    protected Instant disabledAt = null;

    @Column
    protected Instant deletedAt = null;
}
