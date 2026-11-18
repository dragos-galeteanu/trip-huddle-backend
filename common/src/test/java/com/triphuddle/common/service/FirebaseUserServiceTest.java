package com.triphuddle.common.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.triphuddle.common.security.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class FirebaseUserServiceTest {

    private final FirebaseAuth auth = mock(FirebaseAuth.class);
    private final FirebaseUserService service = new FirebaseUserService();
    private MockedStatic<FirebaseAuth> firebase;

    @BeforeEach
    void stubFirebase() {
        firebase = mockStatic(FirebaseAuth.class);
        firebase.when(FirebaseAuth::getInstance).thenReturn(auth);
    }

    @AfterEach
    void releaseFirebase() {
        firebase.close();
    }

    @Test
    void readsAUser() throws Exception {
        UserRecord user = mock(UserRecord.class);
        given(auth.getUser("uid")).willReturn(user);

        assertThat(service.getUser("uid")).isSameAs(user);
    }

    @Test
    void setsTheRoleClaimAlone() throws Exception {
        service.setUserRole("uid", UserRole.Support);

        then(auth).should().setCustomUserClaims("uid", Map.of("role", "Support"));
    }

    @Test
    void setsTheUserIdAndRoleClaimsTogether() throws Exception {
        UUID userId = UUID.randomUUID();

        service.setUserClaims("uid", userId, UserRole.Customer);

        then(auth).should().setCustomUserClaims("uid", Map.of("userId", userId, "role", "Customer"));
    }

    @Test
    void enablesAndDisablesTheAccount() throws Exception {
        ArgumentCaptor<UserRecord.UpdateRequest> update = ArgumentCaptor.forClass(UserRecord.UpdateRequest.class);

        service.disableUser("uid");
        service.enableUser("uid");

        then(auth).should(org.mockito.Mockito.times(2)).updateUser(update.capture());
        assertThat(update.getAllValues()).extracting(r -> (Object) properties(r).get("localId"))
                .containsExactly("uid", "uid");
        assertThat(update.getAllValues()).extracting(r -> (Object) properties(r).get("disableUser"))
                .containsExactly(true, false);
    }

    @Test
    void deletesTheAccountAndMakesResetLinks() throws Exception {
        given(auth.generatePasswordResetLink("a@b.c")).willReturn("https://reset");

        service.deleteUser("uid");

        then(auth).should().deleteUser("uid");
        assertThat(service.generatePasswordResetLink("a@b.c")).isEqualTo("https://reset");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(UserRecord.UpdateRequest request) {
        return (Map<String, Object>) ReflectionTestUtils.getField(request, "properties");
    }
}
