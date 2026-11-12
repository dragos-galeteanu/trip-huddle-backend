package com.triphuddle.api.user.job;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.triphuddle.common.service.FirebaseUserService;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseRoleSyncJob {

    private final UserRepository userRepository;
    private final FirebaseUserService firebaseUserService;

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final int BATCH_SIZE = 200;

    @Scheduled(fixedDelayString = "${app.firebase-role-sync.interval-ms}")
    @Transactional
    public void syncRoles() {
        int page = 0;
        boolean hasMore = true;
        int totalProcessed = 0;
        int consecutiveFailures = 0;

        while (hasMore && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            Slice<User> slice = userRepository.findUsersWithUnsyncedRoles(
                    PageRequest.of(page, BATCH_SIZE));

            if (slice.isEmpty()) {
                break;
            }

            log.info("Firebase role sync: processing batch {} ({} users)", page + 1, slice.getNumberOfElements());

            for (User user : slice.getContent()) {
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("Firebase role sync aborting after {} consecutive failures — "
                            + "remaining users deferred to next run", consecutiveFailures);
                    return;
                }
                try {
                    firebaseUserService.setUserClaims(user.getFirebaseUid(), user.getId(), user.getRole());
                    user.setFirebaseSyncedRole(user.getRole());
                    userRepository.save(user);
                    consecutiveFailures = 0;
                    totalProcessed++;
                    log.info("Firebase role synced for user {} → {}", user.getId(), user.getRole());
                } catch (FirebaseAuthException e) {
                    if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                        log.warn("Firebase user not found for user {}, marking synced", user.getId());
                        user.setFirebaseSyncedRole(user.getRole());
                        userRepository.save(user);
                        consecutiveFailures = 0;
                        totalProcessed++;
                    } else {
                        log.error("Failed to sync Firebase role for user {}: {}", user.getId(), e.getMessage());
                        consecutiveFailures++;
                    }
                }
            }

            hasMore = slice.hasNext();
            page++;
        }

        if (totalProcessed > 0) {
            log.info("Firebase role sync complete: {} user(s) synced", totalProcessed);
        }
    }
}
