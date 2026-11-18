package com.google.firebase.messaging;

import com.google.firebase.ErrorCode;

/** Real Firebase errors for tests: their constructor is package-private, and a mocked one breaks the recoverer. */
public final class FirebaseErrors {

    private FirebaseErrors() {
    }

    public static FirebaseMessagingException unavailable() {
        return new FirebaseMessagingException(ErrorCode.UNAVAILABLE, "FCM is unavailable");
    }
}
