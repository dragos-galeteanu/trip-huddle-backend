package com.triphuddle.smoke.support;

/**
 * Where the system under test runs, from the environment: the gateway, Mailpit, and the Firebase test project.
 * The defaults are the stack scripts/smoke-stack.sh starts on this machine.
 */
public final class SmokeStack {

    private SmokeStack() {
    }

    public static String baseUrl() {
        return env("SMOKE_BASE_URL", "http://localhost:8080");
    }

    public static String mailpitUrl() {
        return env("SMOKE_MAILPIT_URL", "http://localhost:8025");
    }

    /** The Firebase test project's web API key, for signing users up and in as the app does. */
    public static String firebaseApiKey() {
        return required("SMOKE_FIREBASE_API_KEY");
    }

    /** Tags what tests create, so data left on a shared deployment can be swept. */
    public static String tag() {
        return "smoke-";
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set; see smoke-tests/docs/README.md");
        }
        return value;
    }
}
