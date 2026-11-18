package com.triphuddle.smoke.support;

import com.triphuddle.smoke.support.Http.Reply;

import java.util.UUID;

/** A real user of the system, signed in through the Firebase test project, calling the gateway. */
public record Actor(UUID userId, String email, String token) {

    public Reply get(String path) {
        return Http.send("GET", SmokeStack.baseUrl() + path, token, null);
    }

    public Reply post(String path, Object body) {
        return Http.send("POST", SmokeStack.baseUrl() + path, token, body);
    }

    public Reply put(String path, Object body) {
        return Http.send("PUT", SmokeStack.baseUrl() + path, token, body);
    }

    public Reply patch(String path, Object body) {
        return Http.send("PATCH", SmokeStack.baseUrl() + path, token, body);
    }

    public Reply delete(String path) {
        return Http.send("DELETE", SmokeStack.baseUrl() + path, token, null);
    }

    /** Someone without a token. */
    public static Reply anonymous(String method, String path, Object body) {
        return Http.send(method, SmokeStack.baseUrl() + path, null, body);
    }
}
