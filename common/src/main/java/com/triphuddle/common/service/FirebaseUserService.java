package com.triphuddle.common.service;

import com.triphuddle.common.security.UserRole;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing Firebase users and custom claims.
 * Handles setting user roles as custom claims in Firebase tokens.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseUserService {

    /**
     * Get a Firebase user.
     *
     * @param firebaseUid the Firebase UID of the user
     * @return the Firebase user record
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public UserRecord getUser(String firebaseUid) throws FirebaseAuthException {
        return FirebaseAuth.getInstance().getUser(firebaseUid);
    }

    /**
     * Set the {@code role} custom claim of a Firebase user, replacing any other custom claims.
     *
     * @param firebaseUid the Firebase UID of the user
     * @param userRole the role to set
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public void setUserRole(String firebaseUid, UserRole userRole) throws FirebaseAuthException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userRole.name());

        FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, claims);
        log.info("Set role {} for user {}", userRole, firebaseUid);
    }

    /**
     * Set the {@code userId} and {@code role} custom claims of a Firebase user.
     *
     * @param firebaseUid the Firebase UID of the user
     * @param userId the platform ID of the user
     * @param userRole the role to set
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public void setUserClaims(String firebaseUid, UUID userId, UserRole userRole) throws FirebaseAuthException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", userRole.name());

        FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, claims);
        log.info("Set role {} and userId {} for user {}", userRole, userId, firebaseUid);
    }

    /**
     * Let a Firebase user sign in again.
     *
     * @param firebaseUid the Firebase UID of the user
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public void enableUser(String firebaseUid) throws FirebaseAuthException {
        UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(firebaseUid)
                .setDisabled(false);

        FirebaseAuth.getInstance().updateUser(request);
        log.info("User {} has been enabled", firebaseUid);
    }

    /**
     * Stop a Firebase user from signing in.
     *
     * @param firebaseUid the Firebase UID of the user
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public void disableUser(String firebaseUid) throws FirebaseAuthException {
        UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(firebaseUid)
                .setDisabled(true);

        FirebaseAuth.getInstance().updateUser(request);
        log.info("User {} has been disabled", firebaseUid);
    }

    /**
     * Delete a Firebase user.
     *
     * @param firebaseUid the Firebase UID of the user
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public void deleteUser(String firebaseUid) throws FirebaseAuthException {
        FirebaseAuth.getInstance().deleteUser(firebaseUid);
        log.info("User {} has been deleted", firebaseUid);
    }

    /**
     * Generate a Firebase password reset link.
     *
     * @param email the email address of the user
     * @return the password reset link
     * @throws FirebaseAuthException if Firebase rejects the call
     */
    public String generatePasswordResetLink(String email) throws FirebaseAuthException {
        return FirebaseAuth.getInstance().generatePasswordResetLink(email);
    }
}
