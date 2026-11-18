package com.triphuddle.smoke.requirements;

import com.triphuddle.smoke.support.Actor;
import com.triphuddle.smoke.support.Actors;
import com.triphuddle.smoke.support.Requirement;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Reference data: open to read, closed to change, and passed on to api-destination. */
@Tag("smoke")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PlatformSmokeTest {

    /** A country with codes ISO leaves to users, so it never meets the seeded ones. */
    static Map<String, Object> country() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String second = String.valueOf((char) ('B' + random.nextInt(25)));
        Map<String, Object> country = new HashMap<>();
        country.put("alpha2", "Q" + second);
        country.put("alpha3", "Q" + second + (char) ('A' + random.nextInt(26)));
        country.put("numericCode", String.valueOf(900 + random.nextInt(100)));
        country.put("name", "Smokeland " + random.nextInt(1_000_000));
        country.put("isEuVatMember", false);
        country.put("isEuMember", false);
        country.put("isEeaMember", false);
        country.put("isSchengenMember", false);
        return country;
    }

    @Test
    @Requirement("PLT-2")
    void the_lists_are_read_without_a_token_and_changed_only_by_admins_and_support() {
        assertThat(Actor.anonymous("GET", "/api/v1/platform/currencies?page=1&pageSize=5", null).status())
                .isEqualTo(200);
        assertThat(Actors.customer().post("/api/v1/platform/countries", country()).status()).isEqualTo(403);
        assertThat(Actors.support().post("/api/v1/platform/countries", country()).status()).isIn(201, 422);
    }

    @Test
    @Requirement("PLT-3")
    void a_country_created_here_reaches_api_destination() {
        Actor admin = Actors.admin();
        Map<String, Object> country = country();
        admin.post("/api/v1/platform/countries", country).expect(201);

        // api-destination takes places only in countries it has been told about.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(admin.post(
                "/api/v1/destinations/places", Map.of("countryCode", country.get("alpha2"), "kind", "CITY",
                        "name", "Smoke City")).status()).isEqualTo(201));
    }
}
