package com.triphuddle.api.user.api;

import com.triphuddle.api.user.fixtures.UserFixtures;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Users straight into the table, for tests about what happens to an existing user.
 */
final class UserTestData {

    private UserTestData() {
    }

    static UUID insertUser(JdbcTemplate jdbc, boolean isVerified) {
        UUID id = UUID.randomUUID();
        int n = UserFixtures.next();
        jdbc.update("""
                insert into users (id, firebase_uid, email, phone_number, first_name, last_name, role,
                    email_verified, created_at, updated_at)
                values (?, ?, ?, ?, 'Ana', 'Pop', 'Customer', ?, now(), now())""",
                id, "firebase-it-" + n, "it" + n + "@example.com", "+4071%07d".formatted(n), isVerified);
        return id;
    }
}
