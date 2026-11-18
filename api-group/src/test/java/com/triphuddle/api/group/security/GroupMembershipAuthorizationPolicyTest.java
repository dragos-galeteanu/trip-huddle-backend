package com.triphuddle.api.group.security;

import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GroupMembershipAuthorizationPolicyTest extends AbstractAuthorizationPolicyTest<CreateGroupMembershipRequest,
        UpdateGroupMembershipRequest, GroupMembershipAuthorizationPolicy> {

    private final GroupMembershipRepository repository = mock(GroupMembershipRepository.class);
    private final GroupAccess access = mock(GroupAccess.class);

    @Override
    protected GroupMembershipAuthorizationPolicy policy() {
        return new GroupMembershipAuthorizationPolicy(repository, access);
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return GroupMatrices.GROUP_PARTS;
    }

    @Override
    protected CreateGroupMembershipRequest createRequest() {
        return GroupFixtures.createMembership(UUID.randomUUID()).build();
    }

    @Override
    protected UpdateGroupMembershipRequest updateRequest() {
        return GroupFixtures.updateMembership(UUID.randomUUID(), GroupRole.MEMBER);
    }

    @Nested
    class InsideTheGroup {

        private final Group group = GroupFixtures.group();
        private final Authentication customer = Caller.CUSTOMER.authentication();

        @Test
        void aMemberReadsMembershipsOfTheirGroupButCannotManageThem() {
            var membership = GroupFixtures.membership(group, GroupRole.MEMBER);
            given(repository.findById(membership.getId())).willReturn(Optional.of(membership));
            given(access.isGroupMember(customer, group.getId())).willReturn(true);

            assertThat(policy().canGetById(customer, membership.getId())).isTrue();
            assertThat(policy().canGetAllByGroup(customer, group.getId())).isTrue();
            assertThat(policy().canCreate(customer, GroupFixtures.createMembership(group.getId()).build())).isFalse();
            assertThat(policy().canUpdate(customer, GroupFixtures.updateMembership(membership.getId(),
                    GroupRole.ADMIN))).isFalse();
        }

        @Test
        void aGroupAdminAddsChangesAndRemovesMembers() {
            var membership = GroupFixtures.membership(group, GroupRole.MEMBER);
            given(repository.findById(membership.getId())).willReturn(Optional.of(membership));
            given(access.isGroupAdmin(customer, group.getId())).willReturn(true);

            assertThat(policy().canCreate(customer, GroupFixtures.createMembership(group.getId()).build())).isTrue();
            assertThat(policy().canUpdate(customer, GroupFixtures.updateMembership(membership.getId(),
                    GroupRole.ADMIN))).isTrue();
            assertThat(policy().canDelete(customer, membership.getId())).isTrue();
        }

        @Test
        void aMemberLeavesTheGroupByDeletingTheirOwnMembership() {
            var membership = GroupFixtures.membership(group, GroupRole.MEMBER);
            given(repository.findById(membership.getId())).willReturn(Optional.of(membership));

            assertThat(policy().canDelete(Caller.CUSTOMER.authentication(membership.getUserId()),
                    membership.getId())).isTrue();
            assertThat(policy().canDelete(customer, membership.getId())).isFalse();
        }

        @Test
        void aMembershipThatDoesNotExistBelongsToNoGroup() {
            assertThat(policy().canGetById(customer, UUID.randomUUID())).isFalse();
            assertThat(policy().canGetById(customer, null)).isFalse();
        }
    }
}
