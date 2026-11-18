package com.triphuddle.smoke.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.triphuddle.smoke.support.Http.Reply;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates real users per test, as the app does: a Firebase sign-up, registration through the gateway, and a refreshed
 * token carrying the {@code userId} and {@code role} claims. Staff get their role through the Admin SDK, with the
 * test project's credentials in GOOGLE_APPLICATION_CREDENTIALS.
 */
public final class Actors {

    private static final String IDENTITY = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String SECURE_TOKEN = "https://securetoken.googleapis.com/v1/token?key=";

    private Actors() {
    }

    public static String freshEmail() {
        return SmokeStack.tag() + UUID.randomUUID() + "@example.com";
    }

    /** A registered customer. */
    public static Actor customer() {
        return signUp(freshEmail());
    }

    public static Actor support() {
        return withRole(customer(), "Support");
    }

    public static Actor admin() {
        return withRole(customer(), "Admin");
    }

    /** Signs up with this email and registers, as a new user of the app does. */
    public static Actor signUp(String email) {
        JsonNode account = Http.send("POST", IDENTITY + "signUp?key=" + SmokeStack.firebaseApiKey(), null,
                Map.of("email", email, "password", "Smoke-" + UUID.randomUUID(), "returnSecureToken", true))
                .expect(200).body();
        String phone = "+4070" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        Reply registered = Http.send("POST", SmokeStack.baseUrl() + "/api/v1/users/register",
                account.get("idToken").asText(), Map.of("firstName", "Smoke", "lastName", "Tester",
                        "phoneNumber", phone)).expect(201);
        String token = refreshed(account.get("refreshToken").asText());
        return new Actor(UUID.fromString(registered.body().get("id").asText()), email, token);
    }

    static Actor withRole(Actor actor, String role) {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance(app());
            String uid = auth.getUserByEmail(actor.email()).getUid();
            Map<String, Object> claims = new HashMap<>(auth.getUser(uid).getCustomClaims());
            claims.put("role", role);
            auth.setCustomUserClaims(uid, claims);
            String token = auth.createCustomToken(uid);
            JsonNode signedIn = Http.send("POST", IDENTITY + "signInWithCustomToken?key="
                    + SmokeStack.firebaseApiKey(), null, Map.of("token", token, "returnSecureToken", true))
                    .expect(200).body();
            return new Actor(actor.userId(), actor.email(), signedIn.get("idToken").asText());
        } catch (FirebaseAuthException e) {
            throw new IllegalStateException("Could not give " + actor.email() + " the role " + role, e);
        }
    }

    private static String refreshed(String refreshToken) {
        return Http.send("POST", SECURE_TOKEN + SmokeStack.firebaseApiKey(), null,
                Map.of("grant_type", "refresh_token", "refresh_token", refreshToken)).expect(200).body()
                .get("id_token").asText();
    }

    private static synchronized FirebaseApp app() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        try {
            return FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault()).build());
        } catch (IOException e) {
            throw new UncheckedIOException("Set GOOGLE_APPLICATION_CREDENTIALS to the test project's key", e);
        }
    }
}
