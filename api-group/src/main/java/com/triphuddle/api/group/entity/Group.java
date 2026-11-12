package com.triphuddle.api.group.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "groups")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Group extends BaseJpaEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;
}
