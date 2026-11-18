package com.triphuddle.testsupport.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Puts a caller in the security context for code that reads it directly, and takes it out again on close:
 * {@code try (SignedIn ignored = SignedIn.as(Caller.CUSTOMER, userId)) { ... }}.
 */
public final class SignedIn implements AutoCloseable {

    private SignedIn() {
    }

    public static SignedIn as(Caller caller, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(caller.authentication(userId));
        return new SignedIn();
    }

    @Override
    public void close() {
        SecurityContextHolder.clearContext();
    }
}
