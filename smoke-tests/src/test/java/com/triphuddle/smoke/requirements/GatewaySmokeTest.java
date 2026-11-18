package com.triphuddle.smoke.requirements;

import com.triphuddle.smoke.support.Actor;
import com.triphuddle.smoke.support.Actors;
import com.triphuddle.smoke.support.Requirement;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tokens at the gateway and again at the service, through the running system. */
@Tag("smoke")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GatewaySmokeTest {

    @Test
    @Requirement({"GW-1", "NFR-1"})
    void a_request_without_a_token_is_turned_away_unless_the_route_is_public() {
        assertThat(Actor.anonymous("GET", "/api/v1/users/me", null).status()).isEqualTo(401);
        assertThat(Actor.anonymous("GET", "/api/v1/destinations/places?page=1&pageSize=1", null).status())
                .isEqualTo(401);
        assertThat(Actor.anonymous("GET", "/api/v1/platform/countries?page=1&pageSize=1", null).status())
                .isEqualTo(200);
        assertThat(Actor.anonymous("GET", "/api/v1/licenses?page=1&pageSize=1", null).status()).isEqualTo(200);
    }

    @Test
    @Requirement("NFR-1")
    void a_signed_in_user_is_still_checked_by_the_service_behind_the_gateway() {
        Actor customer = Actors.customer();

        assertThat(customer.get("/api/v1/chats/support-tickets?limit=5").status()).isEqualTo(403);
        assertThat(customer.post("/api/v1/platform/countries", Map.of()).status()).isEqualTo(403);
        assertThat(customer.get("/api/v1/users/me").status()).isEqualTo(200);
    }
}
