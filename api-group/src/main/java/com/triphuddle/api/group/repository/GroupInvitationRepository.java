package com.triphuddle.api.group.repository;

import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, UUID> {

    Optional<GroupInvitation> findByTokenAndDeletedAtIsNullAndDisabledAtIsNull(UUID token);

    boolean existsByGroupIdAndEmailAndStatusAndDeletedAtIsNull(UUID groupId, String email, GroupInvitationStatus status);

    Page<GroupInvitation> findByGroupIdAndDeletedAtIsNull(UUID groupId, Pageable pageable);

    List<GroupInvitation> findByEmailAndStatusAndAcceptedByUserIdIsNullAndDeletedAtIsNullAndDisabledAtIsNull(
            String email, GroupInvitationStatus status);
}
