package com.triphuddle.api.group.messaging;

import com.triphuddle.amqp.user.UserEmailVerifiedEvent;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationEvent;
import com.triphuddle.api.group.service.GroupInvitationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class UserEventListenerTest {

    private final GroupInvitationService invitations = mock(GroupInvitationService.class);
    private final UserEventListener listener = new UserEventListener(invitations);

    @Test
    void linksTheUserApiUserMatchedToAnInvitation() {
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        listener.onUserMatchedToGroupInvitation(new UserMatchedToGroupInvitationEvent(invitationId, userId));

        then(invitations).should().linkUser(invitationId, userId);
    }

    @Test
    void linksEveryAcceptedInvitationOnceAnEmailIsVerified() {
        UUID userId = UUID.randomUUID();

        listener.onUserEmailVerified(new UserEmailVerifiedEvent(userId, "friend@example.com"));

        then(invitations).should().linkVerifiedEmail(userId, "friend@example.com");
    }
}
