package com.triphuddle.api.user.job;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.repository.UserRepository;
import com.triphuddle.common.security.UserRole;
import com.triphuddle.common.service.FirebaseUserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * USR-2: claims Firebase couldn't take at registration are set later.
 */
class FirebaseRoleSyncJobTest {

    private final UserRepository users = mock(UserRepository.class);
    private final FirebaseUserService firebase = mock(FirebaseUserService.class);
    private final FirebaseRoleSyncJob job = new FirebaseRoleSyncJob(users, firebase);

    @Test
    void setsTheClaimsOfEveryUnsyncedUserPageByPage() throws Exception {
        User first = UserFixtures.user();
        User second = UserFixtures.user();
        second.setRole(UserRole.Support);
        given(users.findUsersWithUnsyncedRoles(PageRequest.of(0, 200)))
                .willReturn(new SliceImpl<>(List.of(first), PageRequest.of(0, 200), true));
        given(users.findUsersWithUnsyncedRoles(PageRequest.of(1, 200)))
                .willReturn(new SliceImpl<>(List.of(second), PageRequest.of(1, 200), false));

        job.syncRoles();

        then(firebase).should().setUserClaims(first.getFirebaseUid(), first.getId(), UserRole.Customer);
        then(firebase).should().setUserClaims(second.getFirebaseUid(), second.getId(), UserRole.Support);
        assertThat(second.getFirebaseSyncedRole()).isEqualTo(UserRole.Support);
    }

    @Test
    void marksAUserWithoutAFirebaseAccountSyncedSoItIsNotRetriedForever() throws Exception {
        User gone = UserFixtures.user();
        given(users.findUsersWithUnsyncedRoles(any())).willReturn(new SliceImpl<>(List.of(gone)));
        willThrow(failure(AuthErrorCode.USER_NOT_FOUND)).given(firebase).setUserClaims(any(), any(), any());

        job.syncRoles();

        assertThat(gone.getFirebaseSyncedRole()).isEqualTo(UserRole.Customer);
        then(users).should().save(gone);
    }

    @Test
    void stopsAfterThreeFailuresInARowAndLeavesTheRestForTheNextRun() throws Exception {
        List<User> batch = IntStream.range(0, 5).mapToObj(i -> UserFixtures.user()).toList();
        given(users.findUsersWithUnsyncedRoles(any())).willReturn(new SliceImpl<>(batch));
        willThrow(failure(AuthErrorCode.CONFIGURATION_NOT_FOUND)).given(firebase).setUserClaims(any(), any(), any());

        job.syncRoles();

        then(firebase).should(times(3)).setUserClaims(any(), any(), any());
        then(users).should(never()).save(any());
    }

    @Test
    void doesNothingWhenEveryoneIsSynced() throws Exception {
        given(users.findUsersWithUnsyncedRoles(any())).willReturn(new SliceImpl<>(List.of()));

        job.syncRoles();

        then(firebase).shouldHaveNoInteractions();
    }

    private static FirebaseAuthException failure(AuthErrorCode code) {
        FirebaseAuthException exception = mock(FirebaseAuthException.class);
        given(exception.getAuthErrorCode()).willReturn(code);
        return exception;
    }
}
