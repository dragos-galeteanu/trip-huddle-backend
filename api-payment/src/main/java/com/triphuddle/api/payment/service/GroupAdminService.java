package com.triphuddle.api.payment.service;

import com.triphuddle.api.payment.entity.GroupAdmin;
import com.triphuddle.api.payment.repository.GroupAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Keeps the local copy of group admins in step with api-group's membership events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupAdminService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final GroupAdminRepository groupAdminRepository;

    /**
     * Record a membership that was created or changed: an ADMIN one is kept, any other is forgotten.
     *
     * @param groupMembershipId the ID of the membership
     * @param groupId the ID of its group
     * @param userId the ID of its user
     * @param role its role, ADMIN or MEMBER
     */
    public void onMembershipChanged(UUID groupMembershipId, UUID groupId, UUID userId, String role) {
        if (!ADMIN_ROLE.equals(role)) {
            onMembershipRemoved(groupMembershipId);
            return;
        }
        // Upserted by membership ID, so a repeated or out-of-order event does no harm.
        GroupAdmin admin = groupAdminRepository.findByGroupMembershipId(groupMembershipId).orElseGet(GroupAdmin::new);
        admin.setGroupMembershipId(groupMembershipId);
        admin.setGroupId(groupId);
        admin.setUserId(userId);
        groupAdminRepository.save(admin);
        log.info("Group admin recorded [groupMembershipId={}, groupId={}, userId={}]", groupMembershipId, groupId,
                userId);
    }

    /**
     * Forget a membership that was disabled or deleted.
     *
     * @param groupMembershipId the ID of the membership
     */
    public void onMembershipRemoved(UUID groupMembershipId) {
        if (groupAdminRepository.deleteByGroupMembershipId(groupMembershipId) > 0) {
            log.info("Group admin removed [groupMembershipId={}]", groupMembershipId);
        }
    }

    /**
     * Forget the admins of a group that was disabled or deleted.
     *
     * @param groupId the ID of the group
     */
    public void onGroupRemoved(UUID groupId) {
        int removed = groupAdminRepository.deleteByGroupId(groupId);
        log.info("Group removed [groupId={}, adminsRemoved={}]", groupId, removed);
    }
}
