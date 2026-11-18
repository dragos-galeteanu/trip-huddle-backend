package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipDeletedEvent;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.platform.CountryDeletedEvent;
import com.triphuddle.amqp.platform.CountryDisabledEvent;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DST-1 (access), DST-2 (the place tree) and DST-4 (text search), over HTTP. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PlacesIT extends DestinationApiSupport {

    @BeforeEach
    void countries() {
        country("pt");
        country("ES");
    }

    @Nested
    class Access {

        @Test
        void a_subscriber_browses_and_anyone_else_is_turned_away() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));

            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, subscriber()).andExpect(status().isOk());
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, UUID.randomUUID()).andExpect(status().isForbidden());
            call(get(PLACES + "/" + lisbon), Caller.ANONYMOUS, null).andExpect(status().isUnauthorized());
            call(get(PLACES + "/" + lisbon), Caller.INSPECTOR, UUID.randomUUID()).andExpect(status().isOk());
        }

        @Test
        void access_lasts_48_hours_past_the_periods_end_for_a_renewal_on_its_way() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            UUID user = UUID.randomUUID();
            UUID subscription = license(LicenseTarget.USER, user, Map.of(ACCESS, "true"),
                    Instant.now().plus(Duration.ofHours(1)));

            clock.advance(Duration.ofHours(40));
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, user).andExpect(status().isOk());
            clock.advance(Duration.ofHours(10));
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, user).andExpect(status().isForbidden());

            licenseEvents.onSubscriptionRenewed(new LicenseSubscriptionRenewedEvent(subscription, UUID.randomUUID(),
                    LicenseTarget.USER, user, Instant.now().plus(Duration.ofDays(30)), 999));
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, user).andExpect(status().isOk());
        }

        @Test
        void an_ended_subscription_or_a_switched_off_entitlement_gives_no_access() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            UUID ended = UUID.randomUUID();
            UUID subscription = license(LicenseTarget.USER, ended, Map.of(ACCESS, "true"),
                    Instant.now().plus(Duration.ofDays(30)));
            UUID switchedOff = UUID.randomUUID();
            UUID licenseOfSwitchedOff = jdbc.queryForObject("select license_id from "
                    + "destination_license_subscriptions where subscription_id = ?", UUID.class,
                    license(LicenseTarget.USER, switchedOff, Map.of(ACCESS, "true"),
                            Instant.now().plus(Duration.ofDays(30))));

            licenseEvents.onSubscriptionEnded(new LicenseSubscriptionEndedEvent(subscription, UUID.randomUUID(),
                    "Explorer", LicenseTarget.USER, ended, ended, "CANCELLED", "USER", Instant.now()));
            licenseEvents.onEntitlementsChanged(new LicenseEntitlementsChangedEvent(licenseOfSwitchedOff, "EXPLORER",
                    Map.of(ACCESS, "off")));

            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, ended).andExpect(status().isForbidden());
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, switchedOff).andExpect(status().isForbidden());
            // A late activation does not bring an ended subscription back.
            licenseEvents.onSubscriptionActivated(new com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent(
                    subscription, UUID.randomUUID(), "EXPLORER", "Explorer", LicenseTarget.USER, ended, ended,
                    Instant.now().plus(Duration.ofDays(30)), true, false, 999, "EUR", Map.of(ACCESS, "true")));
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, ended).andExpect(status().isForbidden());
        }

        @Test
        void a_groups_license_counts_for_its_members_acting_for_the_group() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            UUID groupId = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            license(LicenseTarget.GROUP, groupId, Map.of(ACCESS, "true"), Instant.now().plus(Duration.ofDays(30)));
            member(groupId, member, "MEMBER");
            String asGroup = PLACES + "/" + lisbon + "?groupId=" + groupId;

            call(get(asGroup), Caller.CUSTOMER, member).andExpect(status().isOk());
            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, member).andExpect(status().isForbidden());
            call(get(asGroup), Caller.CUSTOMER, UUID.randomUUID()).andExpect(status().isForbidden());

            UUID membership = jdbc.queryForObject("select group_membership_id from destination_group_members "
                    + "where user_id = ?", UUID.class, member);
            groupEvents.onMembershipDeleted(new GroupMembershipDeletedEvent(membership, groupId, member, "MEMBER"));
            call(get(asGroup), Caller.CUSTOMER, member).andExpect(status().isForbidden());

            member(groupId, member, "ADMIN");
            groupEvents.onGroupDeleted(new GroupDeletedEvent(groupId, "Trip"));
            call(get(asGroup), Caller.CUSTOMER, member).andExpect(status().isForbidden());
        }

        @Test
        void a_disabled_membership_or_group_stops_access_and_a_changed_membership_keeps_it() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            UUID groupId = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            license(LicenseTarget.GROUP, groupId, Map.of(ACCESS, "true"), Instant.now().plus(Duration.ofDays(30)));
            UUID membership = UUID.randomUUID();
            String asGroup = PLACES + "/" + lisbon + "?groupId=" + groupId;

            groupEvents.onMembershipUpdated(new com.triphuddle.amqp.group.GroupMembershipUpdatedEvent(membership,
                    groupId, member, "ADMIN"));
            call(get(asGroup), Caller.CUSTOMER, member).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select role from destination_group_members where group_membership_id = ?",
                    String.class, membership)).isEqualTo("ADMIN");
            groupEvents.onMembershipDisabled(new com.triphuddle.amqp.group.GroupMembershipDisabledEvent(membership,
                    groupId, member, "ADMIN"));
            call(get(asGroup), Caller.CUSTOMER, member).andExpect(status().isForbidden());

            member(groupId, member, "MEMBER");
            groupEvents.onGroupDisabled(new com.triphuddle.amqp.group.GroupDisabledEvent(groupId, "Trip"));
            call(get(asGroup), Caller.CUSTOMER, member).andExpect(status().isForbidden());
        }

        @Test
        void an_end_that_arrives_before_the_activation_keeps_the_subscription_ended() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            UUID user = UUID.randomUUID();
            UUID subscription = UUID.randomUUID();
            Instant endedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

            licenseEvents.onSubscriptionEnded(new LicenseSubscriptionEndedEvent(subscription, UUID.randomUUID(),
                    "Explorer", LicenseTarget.USER, user, user, "EXPIRED", "PERIOD_END", endedAt));
            licenseEvents.onSubscriptionActivated(new com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent(
                    subscription, UUID.randomUUID(), "EXPLORER", "Explorer", LicenseTarget.USER, user, user,
                    Instant.now().plus(Duration.ofDays(30)), true, false, 999, "EUR", Map.of(ACCESS, "true")));

            call(get(PLACES + "/" + lisbon), Caller.CUSTOMER, user).andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("select ended_at from destination_license_subscriptions "
                    + "where subscription_id = ?", java.sql.Timestamp.class, subscription).toInstant())
                    .isEqualTo(endedAt);
        }

        @Test
        void only_admins_and_support_change_places() throws Exception {
            call(post(PLACES), Caller.CUSTOMER, subscriber(), Map.of("countryCode", "PT", "kind", "CITY",
                    "name", "Porto")).andExpect(status().isForbidden());
            call(post(PLACES), Caller.INSPECTOR, UUID.randomUUID(), Map.of("countryCode", "PT", "kind", "CITY",
                    "name", "Porto")).andExpect(status().isForbidden());
            call(post(PLACES), Caller.SUPPORT, UUID.randomUUID(), Map.of("countryCode", "PT", "kind", "CITY",
                    "name", "Porto")).andExpect(status().isCreated());
        }
    }

    @Nested
    class The_tree {

        @Test
        void places_hang_under_their_country_with_their_ancestors_and_a_free_slug() throws Exception {
            JsonNode region = place("PT", "REGION", "Lisbon District", null);
            JsonNode city = place("PT", "CITY", "Lisbon", idOf(region), "latitude", 38.72, "longitude", -9.14,
                    "wikidataId", "Q597", "population", 545_000);
            JsonNode again = place("PT", "CITY", "Lisbon", idOf(region));

            assertThat(city.get("slug").asText()).isEqualTo("pt-lisbon");
            assertThat(again.get("slug").asText()).isEqualTo("pt-lisbon-2");
            assertThat(city.get("countryCode").asText()).isEqualTo("PT");
            assertThat(city.get("ancestors").findValuesAsText("id")).containsExactly(region.get("id").asText());
            assertThat(city.get("createdAt").isNull()).isFalse();
            call(get(PLACES + "/slug/pt-lisbon"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.id").value(city.get("id").asText()));
        }

        @Test
        void a_place_needs_an_active_country_and_a_parent_in_it() throws Exception {
            UUID spanish = idOf(place("ES", "CITY", "Madrid", null));
            countryEvents.onCountryDisabled(new CountryDisabledEvent(UUID.randomUUID(), "fr"));
            countryEvents.onCountryDeleted(new CountryDeletedEvent(UUID.randomUUID(), "it"));

            for (Map<String, Object> request : List.<Map<String, Object>>of(
                    Map.of("countryCode", "FR", "kind", "CITY", "name", "Paris"),
                    Map.of("countryCode", "IT", "kind", "CITY", "name", "Rome"),
                    Map.of("kind", "CITY", "name", "Nowhere"),
                    Map.of("countryCode", "PT", "kind", "CITY", "name", "Porto", "parentId", spanish),
                    Map.of("countryCode", "PT", "kind", "CITY", "name", "Porto", "parentId", UUID.randomUUID()),
                    Map.of("countryCode", "PT", "kind", "CITY", "name", "Porto", "latitude", 41.1))) {
                call(post(PLACES), Caller.ADMIN, UUID.randomUUID(), request)
                        .andExpect(status().isUnprocessableEntity());
            }
            call(post(PLACES), Caller.ADMIN, UUID.randomUUID(), Map.of("countryCode", "PRT", "kind", "CITY",
                    "name", "Porto")).andExpect(status().isBadRequest());
        }

        @Test
        void slugs_wikidata_ids_and_osm_ids_belong_to_one_place() throws Exception {
            place("PT", "CITY", "Lisbon", null, "slug", "lisboa", "wikidataId", "Q597", "osmId", "relation/5400890");

            for (Map<String, Object> clash : List.<Map<String, Object>>of(
                    Map.of("countryCode", "PT", "kind", "CITY", "name", "X", "slug", "lisboa"),
                    Map.of("countryCode", "PT", "kind", "CITY", "name", "X", "wikidataId", "Q597"),
                    Map.of("countryCode", "PT", "kind", "CITY", "name", "X", "osmId", "relation/5400890"))) {
                call(post(PLACES), Caller.ADMIN, UUID.randomUUID(), clash).andExpect(status().isUnprocessableEntity());
            }
        }

        @Test
        void an_update_keeps_its_own_ids_and_replaces_names_categories_and_tags() throws Exception {
            JsonNode lisbon = place("PT", "CITY", "Lisbon", null, "wikidataId", "Q597",
                    "names", List.of(Map.of("languageCode", "pt", "name", "Lisboa")), "tags", List.of("capital"));
            String path = PLACES + "/" + lisbon.get("id").asText();

            call(put(path), Caller.ADMIN, UUID.randomUUID(), Map.of("kind", "CITY", "name", "Lisbon",
                    "wikidataId", "Q597", "slug", "lisbon", "tags", List.of("coast"),
                    "names", List.of(Map.of("languageCode", "de", "name", "Lissabon"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("lisbon"))
                    .andExpect(jsonPath("$.tags[0]").value("coast"));

            JsonNode names = body(call(get(path), Caller.ADMIN, UUID.randomUUID())).get("names");
            assertThat(names.findValuesAsText("name")).contains("Lissabon", "Lisbon").doesNotContain("Lisboa");
            call(put(path), Caller.ADMIN, UUID.randomUUID(), Map.of("kind", "CITY", "name", "Lisbon",
                    "categoryIds", List.of(UUID.randomUUID()))).andExpect(status().isUnprocessableEntity());
            call(put(PLACES + "/" + UUID.randomUUID()), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("kind", "CITY", "name", "Ghost")).andExpect(status().isNotFound());
        }

        @Test
        void moving_a_place_moves_everything_inside_it() throws Exception {
            UUID north = idOf(place("PT", "REGION", "North", null));
            UUID south = idOf(place("PT", "REGION", "South", null));
            UUID city = idOf(place("PT", "CITY", "Porto", south));
            UUID district = idOf(place("PT", "DISTRICT", "Ribeira", city));

            call(post(PLACES + "/" + city + "/move"), Caller.ADMIN, UUID.randomUUID(), Map.of("parentId", north))
                    .andExpect(status().isOk());

            JsonNode moved = body(call(get(PLACES + "/" + district), Caller.ADMIN, UUID.randomUUID()));
            assertThat(moved.get("ancestors").findValuesAsText("id")).containsExactly(north.toString(),
                    city.toString());
            call(post(PLACES + "/" + north + "/move"), Caller.ADMIN, UUID.randomUUID(), Map.of("parentId", district))
                    .andExpect(status().isUnprocessableEntity());
            call(post(PLACES + "/" + city + "/move"), Caller.ADMIN, UUID.randomUUID(), Map.of())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ancestors.length()").value(0));
        }

        @Test
        void a_merged_place_answers_with_its_target_which_takes_its_names_ids_and_children() throws Exception {
            UUID target = idOf(place("PT", "CITY", "Lisbon", null));
            UUID duplicate = idOf(place("PT", "CITY", "Lisboa", null, "wikidataId", "Q597", "tags", List.of("capital")));
            UUID child = idOf(place("PT", "DISTRICT", "Alfama", duplicate));

            call(post(PLACES + "/" + duplicate + "/merge"), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("targetId", target))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wikidataId").value("Q597"));

            JsonNode answered = body(call(get(PLACES + "/" + duplicate), Caller.CUSTOMER, subscriber()));
            assertThat(answered.get("id").asText()).isEqualTo(target.toString());
            assertThat(answered.get("names").findValuesAsText("name")).contains("Lisboa");
            assertThat(answered.get("tags").toString()).isEqualTo("[\"capital\"]");
            assertThat(body(call(get(PLACES + "/" + child), Caller.ADMIN, UUID.randomUUID())).get("ancestors")
                    .findValuesAsText("id")).containsExactly(target.toString());

            call(post(PLACES + "/" + duplicate + "/merge"), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("targetId", target)).andExpect(status().isUnprocessableEntity());
            call(post(PLACES + "/" + target + "/merge"), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("targetId", target)).andExpect(status().isUnprocessableEntity());
            call(post(PLACES + "/" + target + "/merge"), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("targetId", idOf(place("ES", "CITY", "Madrid", null))))
                    .andExpect(status().isUnprocessableEntity());
            call(post(PLACES + "/" + target + "/merge"), Caller.ADMIN, UUID.randomUUID(), Map.of("targetId", child))
                    .andExpect(status().isUnprocessableEntity());
            call(put(PLACES + "/" + duplicate), Caller.ADMIN, UUID.randomUUID(), Map.of("kind", "CITY", "name", "X"))
                    .andExpect(status().isUnprocessableEntity());
            call(post(PLACES), Caller.ADMIN, UUID.randomUUID(), Map.of("countryCode", "PT", "kind", "DISTRICT",
                    "name", "Graça", "parentId", duplicate)).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("was merged")));
        }

        @Test
        void a_hidden_place_is_for_staff_only_and_a_deleted_one_for_nobody() throws Exception {
            UUID hidden = idOf(place("PT", "CITY", "Secret", null));
            UUID parent = idOf(place("PT", "REGION", "Region", null));
            UUID child = idOf(place("PT", "CITY", "Child", parent));
            UUID customer = subscriber();

            call(post(PLACES + "/" + hidden + "/hide"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.status").value("HIDDEN"));
            call(get(PLACES + "/" + hidden), Caller.CUSTOMER, customer).andExpect(status().isNotFound());
            call(get(PLACES + "/" + hidden), Caller.SUPPORT, UUID.randomUUID()).andExpect(status().isOk());
            call(post(PLACES + "/" + hidden + "/unhide"), Caller.ADMIN, UUID.randomUUID());
            call(get(PLACES + "/" + hidden), Caller.CUSTOMER, customer).andExpect(status().isOk());

            call(delete(PLACES + "/" + parent), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isUnprocessableEntity());
            call(delete(PLACES + "/" + child), Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNoContent());
            call(get(PLACES + "/" + child), Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNotFound());
            call(get(PLACES + "/slug/nope"), Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Search {

        String search(String query, UUID user) throws Exception {
            return PLACES + "?page=1&pageSize=20&" + query;
        }

        List<String> found(String query) throws Exception {
            return names(body(call(get(search(query, null)), Caller.CUSTOMER, subscriber())).get("data"));
        }

        @Test
        void text_matches_any_name_ignoring_accents_and_forgiving_typos() throws Exception {
            place("PT", "CITY", "Lisbon", null, "names", List.of(Map.of("languageCode", "pt", "name", "Lisboa")));
            place("ES", "CITY", "Córdoba", null);

            assertThat(found("q=lisboa")).containsExactly("Lisbon");
            assertThat(found("q=cordoba")).containsExactly("Córdoba");
            assertThat(found("q=lisbn")).containsExactly("Lisbon");
            assertThat(found("q=zzzzzz")).isEmpty();
        }

        @Test
        void featured_and_populous_places_come_first_and_points_rank_by_distance() throws Exception {
            place("PT", "CITY", "Small", null, "population", 1_000, "latitude", 41.15, "longitude", -8.61);
            UUID big = idOf(place("PT", "CITY", "Big", null, "population", 500_000, "latitude", 38.72,
                    "longitude", -9.14));
            place("PT", "CITY", "Featured", null, "population", 10, "latitude", 37.0, "longitude", -8.0);
            call(post(PLACES + "/" + idOf(place("PT", "CITY", "Unlisted", null)) + "/hide"), Caller.ADMIN,
                    UUID.randomUUID());
            call(post(PLACES + "/" + body(call(get(PLACES + "/slug/pt-featured"), Caller.ADMIN, UUID.randomUUID()))
                    .get("id").asText() + "/feature"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.isFeatured").value(true));

            assertThat(found("countryCode=pt")).containsExactly("Featured", "Big", "Small");
            assertThat(found("latitude=41.1&longitude=-8.6")).first().isEqualTo("Small");
            assertThat(found("latitude=38.7&longitude=-9.1&radiusKm=10")).containsExactly("Big");
            assertThat(found("west=-9.5&south=38&east=-9&north=39")).containsExactly("Big");
            assertThat(found("isFeatured=true")).containsExactly("Featured");
            call(post(PLACES + "/" + big + "/unfeature"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.isFeatured").value(false));

            JsonNode staff = body(call(get(search("isIncludingHidden=true", null)), Caller.ADMIN, UUID.randomUUID()));
            assertThat(names(staff.get("data"))).contains("Unlisted");
            assertThat(found("isIncludingHidden=true")).doesNotContain("Unlisted");
        }

        @Test
        void filters_narrow_to_a_subtree_a_parent_a_kind_and_a_tag() throws Exception {
            UUID region = idOf(place("PT", "REGION", "Algarve", null));
            UUID city = idOf(place("PT", "CITY", "Faro", region, "tags", List.of("beach")));
            place("PT", "POI", "Old Town", city);
            place("PT", "CITY", "Elsewhere", null);

            assertThat(found("withinId=" + region)).containsExactlyInAnyOrder("Faro", "Old Town");
            assertThat(found("parentId=" + region)).containsExactly("Faro");
            assertThat(found("kind=POI")).containsExactly("Old Town");
            assertThat(found("tag=beach")).containsExactly("Faro");
        }

        @Test
        void a_radius_needs_a_point_and_a_map_area_all_four_edges() throws Exception {
            UUID user = subscriber();

            call(get(search("radiusKm=5", null)), Caller.CUSTOMER, user).andExpect(status().isUnprocessableEntity());
            call(get(search("west=1&east=2", null)), Caller.CUSTOMER, user).andExpect(status().isUnprocessableEntity());
            call(get(search("withinId=" + UUID.randomUUID(), null)), Caller.CUSTOMER, user)
                    .andExpect(status().isNotFound());
            call(get(search("categoryId=" + UUID.randomUUID(), null)), Caller.CUSTOMER, user)
                    .andExpect(status().isNotFound());
        }
    }
}
