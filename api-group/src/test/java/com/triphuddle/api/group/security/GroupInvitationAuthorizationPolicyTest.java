package com.triphuddle.api.group.security;

import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.repository.GroupInvitationRepository;
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

class GroupInvitationAuthorizationPolicyTest extends AbstractAuthorizationPolicyTest<CreateGroupInvitationRequest,
        UpdateGroupInvitationRequest, GroupInvitationAuthorizationPolicy> {

    private final GroupInvitationRepository repository = mock(GroupInvitationRepository.class);
    private final GroupAccess access = mock(GroupAccess.class);

    @Override
    protected GroupInvitationAuthorizationPolicy policy() {
        return new GroupInvitationAuthorizationPolicy(repository, access);
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return GroupMatrices.GROUP_PARTS;
    }

    @Override
    protected CreateGroupInvitationRequest createRequest() {
        return GroupFixtures.createInvitation(UUID.randomUUID()).build();
    }

    @Override
    protected UpdateGroupInvitationRequest updateRequest() {
        return GroupFixtures.updateInvitation(UUID.randomUUID(), GroupRole.MEMBER);
    }

    @Nested
    class InsideTheGroup {

        private final Group group = GroupFixtures.group();
        private final Authentication customer = Caller.CUSTOMER.authentication();

        @Test
        void aMemberReadsAnInvitationOfTheirGroupButNotTheList() {
            var invitation = GroupFixtures.invitation(group, GroupInvitationStatus.PENDING);
            given(repository.findById(invitation.getId())).willReturn(Optional.of(invitation));
            given(access.isGroupMember(customer, group.getId())).willReturn(true);

            assertThat(policy().canGetById(customer, invitation.getId())).isTrue();
            assertThat(policy().canGetAllByGroup(customer, group.getId())).isFalse();
            assertThat(policy().canCancel(customer, invitation.getId())).isFalse();
        }

        @Test
        void aGroupAdminInvitesListsChangesCancelsAndDeletes() {
            var invitation = GroupFixtures.invitation(group, GroupInvitationStatus.PENDING);
            given(repository.findById(invitation.getId())).willReturn(Optional.of(invitation));
            given(access.isGroupAdmin(customer, group.getId())).willReturn(true);

            assertThat(policy().canCreate(customer, GroupFixtures.createInvitation(group.getId()).build())).isTrue();
            assertThat(policy().canGetAllByGroup(customer, group.getId())).isTrue();
            assertThat(policy().canUpdate(customer, GroupFixtures.updateInvitation(invitation.getId(),
                    GroupRole.ADMIN))).isTrue();
            assertThat(policy().canCancel(customer, invitation.getId())).isTrue();
            assertThat(policy().canDelete(customer, invitation.getId())).isTrue();
        }

        @Test
        void staffListAndCancelAnyGroupsInvitations() {
            assertThat(policy().canGetAllByGroup(Caller.INSPECTOR.authentication(), group.getId())).isTrue();
            assertThat(policy().canCancel(Caller.SUPPORT.authentication(), UUID.randomUUID())).isTrue();
            assertThat(policy().canCancel(Caller.INSPECTOR.authentication(), UUID.randomUUID())).isFalse();
            assertThat(policy().canGetById(customer, null)).isFalse();
        }
    }
}
