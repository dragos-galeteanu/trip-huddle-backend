package com.triphuddle.api.group.security;

import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class GroupAccessTest {

    private final GroupMembershipRepository memberships = mock(GroupMembershipRepository.class);
    private final GroupAccess access = new GroupAccess(memberships);
    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void aMemberHasAnActiveMembership() {
        given(memberships.existsByGroupIdAndUserIdAndDeletedAtIsNullAndDisabledAtIsNull(groupId, userId))
                .willReturn(true);

        assertThat(access.isGroupMember(Caller.CUSTOMER.authentication(userId), groupId)).isTrue();
        assertThat(access.isGroupMember(Caller.CUSTOMER.authentication(), groupId)).isFalse();
    }

    @Test
    void aGroupAdminHasAnActiveAdminMembership() {
        given(memberships.existsByGroupIdAndUserIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(groupId, userId,
                GroupRole.ADMIN)).willReturn(true);

        assertThat(access.isGroupAdmin(Caller.CUSTOMER.authentication(userId), groupId)).isTrue();
        assertThat(access.isGroupAdmin(Caller.CUSTOMER.authentication(), groupId)).isFalse();
    }

    @Test
    void nobodyBelongsToNoGroupAndATokenWithoutAPlatformUserBelongsNowhere() {
        JwtAuthenticationToken unregistered = new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").subject("firebase-uid").build(), List.of());

        assertThat(access.isGroupMember(Caller.CUSTOMER.authentication(userId), null)).isFalse();
        assertThat(access.isGroupAdmin(Caller.CUSTOMER.authentication(userId), null)).isFalse();
        assertThat(access.isGroupMember(unregistered, groupId)).isFalse();
        assertThat(access.isGroupAdmin(unregistered, groupId)).isFalse();
        then(memberships).shouldHaveNoInteractions();
    }
}
