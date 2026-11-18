package com.triphuddle.api.group.security;

import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GroupAuthorizationPolicyTest
        extends AbstractAuthorizationPolicyTest<CreateGroupRequest, UpdateGroupRequest, GroupAuthorizationPolicy> {

    private final GroupAccess access = mock(GroupAccess.class);

    @Override
    protected GroupAuthorizationPolicy policy() {
        return new GroupAuthorizationPolicy(access);
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return GroupMatrices.GROUPS;
    }

    @Override
    protected CreateGroupRequest createRequest() {
        return GroupFixtures.createGroup().build();
    }

    @Override
    protected UpdateGroupRequest updateRequest() {
        return GroupFixtures.updateGroup(UUID.randomUUID()).build();
    }

    @Nested
    class InsideTheGroup {

        private final UUID groupId = UUID.randomUUID();
        private final Authentication customer = Caller.CUSTOMER.authentication();

        @Test
        void aMemberReadsTheGroupButCannotChangeIt() {
            given(access.isGroupMember(customer, groupId)).willReturn(true);

            assertThat(policy().canGetById(customer, groupId)).isTrue();
            assertThat(policy().canUpdate(customer, GroupFixtures.updateGroup(groupId).build())).isFalse();
            assertThat(policy().canDelete(customer, groupId)).isFalse();
        }

        @Test
        void aGroupAdminChangesAndDeletesTheGroup() {
            given(access.isGroupAdmin(customer, groupId)).willReturn(true);

            assertThat(policy().canUpdate(customer, GroupFixtures.updateGroup(groupId).build())).isTrue();
            assertThat(policy().canDelete(customer, groupId)).isTrue();
        }

        @Test
        void evenAPlatformAdminCannotDeleteAGroupTheyDoNotAdminister() {
            assertThat(policy().canDelete(Caller.ADMIN.authentication(), groupId)).isFalse();
        }

        @Test
        void anySignedInUserListsTheirOwnGroups() {
            assertThat(policy().canGetMine(customer)).isTrue();
            assertThat(policy().canGetMine(Caller.ANONYMOUS.authentication())).isFalse();
        }
    }
}
