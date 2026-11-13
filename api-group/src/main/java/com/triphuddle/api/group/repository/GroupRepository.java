package com.triphuddle.api.group.repository;

import com.triphuddle.api.group.entity.Group;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GroupRepository extends BaseJpaRepository<Group, UUID> {
}
