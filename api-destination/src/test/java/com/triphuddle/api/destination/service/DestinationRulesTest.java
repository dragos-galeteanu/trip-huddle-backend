package com.triphuddle.api.destination.service;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.config.DestinationProperties;
import com.triphuddle.api.destination.entity.LicenseAccess;
import com.triphuddle.api.destination.entity.VisaRequirement;
import com.triphuddle.api.destination.opendata.WikidataLabel;
import com.triphuddle.api.destination.repository.GroupMemberRepository;
import com.triphuddle.api.destination.repository.ItineraryRepository;
import com.triphuddle.api.destination.repository.LicenseAccessRepository;
import com.triphuddle.api.destination.repository.TripPlanRepository;
import com.triphuddle.api.destination.repository.VisaRule;
import com.triphuddle.api.destination.security.DestinationAuthorizationPolicy;
import com.triphuddle.api.destination.security.TripAuthorizationPolicy;
import com.triphuddle.api.destination.visa.VisaDatasetParser;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DestinationRulesTest {

    @Nested
    class Entitlements {

        final LicenseAccessRepository licenses = mock(LicenseAccessRepository.class);
        final GroupMemberRepository members = mock(GroupMemberRepository.class);
        final AccessService access = new AccessService(licenses, members,
                new DestinationProperties("DESTINATIONS", 48, 500, null), Clock.fixed(Instant.now(), ZoneOffset.UTC));
        final UUID user = UUID.randomUUID();

        void holds(Map<String, String>... entitlements) {
            List<LicenseAccess> held = new java.util.ArrayList<>();
            for (Map<String, String> each : entitlements) {
                LicenseAccess license = new LicenseAccess();
                license.setEntitlements(new HashMap<>(each));
                held.add(license);
            }
            given(licenses.findCurrent(eq(LicenseTarget.USER), eq(user), any())).willReturn(held);
        }

        @Test
        void an_entitlement_is_on_unless_its_value_switches_it_off() {
            Map<String, String> empty = new HashMap<>();
            empty.put("DESTINATIONS", null);
            holds(empty);
            assertThat(access.hasAccess(user, null)).isTrue();
            for (String off : List.of("false", " OFF ", "0", "no")) {
                holds(Map.of("DESTINATIONS", off));
                assertThat(access.hasAccess(user, null)).as(off).isFalse();
            }
            holds(Map.of("OTHER", "true"));
            assertThat(access.hasAccess(user, null)).isFalse();
            assertThat(access.hasAccess(null, null)).isFalse();
        }

        @Test
        void a_quota_is_the_largest_number_any_current_license_gives() {
            holds(Map.of("Q", "3"), Map.of("Q", "true"), Map.of("Q", " 7 "), Map.of("OTHER", "9"));
            assertThat(access.quotaOf(LicenseTarget.USER, user, "Q")).isEqualTo(7);

            holds(Map.of("Q", "2"), Map.of("Q", "UNLIMITED"));
            assertThat(access.quotaOf(LicenseTarget.USER, user, "Q")).isEqualTo(Integer.MAX_VALUE);
            holds();
            assertThat(access.quotaOf(LicenseTarget.USER, user, "Q")).isZero();
        }

        @Test
        void staff_browse_without_a_license_and_only_admins_and_support_manage() {
            DestinationAuthorizationPolicy policy = new DestinationAuthorizationPolicy(access);
            holds();

            assertThat(policy.canBrowse(Caller.INSPECTOR.authentication(), null)).isTrue();
            assertThat(policy.canBrowse(Caller.CUSTOMER.authentication(user), null)).isFalse();
            assertThat(policy.canBrowse(Caller.ANONYMOUS.authentication(), null)).isFalse();
            assertThat(policy.canGetById(Caller.SUPPORT.authentication(), UUID.randomUUID())).isTrue();
            assertThat(policy.canGetAll(Caller.CUSTOMER.authentication(user))).isFalse();
            assertThat(policy.canCreate(Caller.SUPPORT.authentication(), null)).isTrue();
            assertThat(policy.canUpdate(Caller.INSPECTOR.authentication(), null)).isFalse();
            assertThat(policy.canDelete(Caller.ADMIN.authentication(), UUID.randomUUID())).isTrue();
            assertThat(policy.isStaff(Caller.SALES.authentication())).isFalse();
            assertThat(policy.canDisable(Caller.ADMIN.authentication())).isFalse();
        }

        @Test
        void a_plan_is_listed_by_staff_used_only_by_its_travellers_and_never_updated_or_disabled_as_a_whole() {
            TripAuthorizationPolicy policy = new TripAuthorizationPolicy(mock(TripPlanRepository.class),
                    mock(ItineraryRepository.class), mock(GroupMemberRepository.class), access);

            assertThat(policy.canGetAll(Caller.INSPECTOR.authentication())).isTrue();
            assertThat(policy.canGetAll(Caller.CUSTOMER.authentication(user))).isFalse();
            assertThat(policy.canUse(Caller.ANONYMOUS.authentication(), UUID.randomUUID())).isFalse();
            assertThat(policy.canUse(Caller.CUSTOMER.authentication(user), null)).isFalse();
            assertThat(policy.canUse(Caller.CUSTOMER.authentication(user), UUID.randomUUID())).isFalse();
            assertThat(policy.canUpdate(Caller.ADMIN.authentication(), null)).isFalse();
            assertThat(policy.canDisable(Caller.ADMIN.authentication())).isFalse();
        }
    }

    @Nested
    class Datasets {

        @Test
        void the_visa_dataset_turns_into_rules_and_counts_what_it_cannot_read() {
            VisaDatasetParser.Parsed parsed = VisaDatasetParser.parse("""
                    ﻿Passport,Destination,Requirement\r
                    ro,th,30
                    RO,RO,-1
                    RO,KP,0
                    RO,AU,ETA
                    RO,IN,e-visa
                    RO,NP,visa on arrival
                    RO,US,visa required
                    RO,AF,no admission
                    RO,XX,maybe
                    RO,TH
                    ROU,TH,visa free

                    """);

            assertThat(parsed.rules()).containsExactly(
                    new VisaRule("RO", "TH", VisaRequirement.VISA_FREE, 30),
                    new VisaRule("RO", "RO", VisaRequirement.CITIZEN, null),
                    new VisaRule("RO", "AU", VisaRequirement.ETA, null),
                    new VisaRule("RO", "IN", VisaRequirement.E_VISA, null),
                    new VisaRule("RO", "NP", VisaRequirement.VISA_ON_ARRIVAL, null),
                    new VisaRule("RO", "US", VisaRequirement.VISA_REQUIRED, null),
                    new VisaRule("RO", "AF", VisaRequirement.NO_ADMISSION, null));
            assertThat(parsed.skipped()).isEqualTo(4);
            assertThatThrownBy(() -> VisaDatasetParser.parse("a,b,c\n")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void an_imported_place_is_named_in_english_else_locally_else_in_any_language() {
            List<WikidataLabel> labels = List.of(new WikidataLabel("Q1", "de", "Lissabon"),
                    new WikidataLabel("Q1", "pt", "Lisboa"), new WikidataLabel("Q1", "fr", " "));

            assertThat(ImportService.nameOf(labels, List.of("pt"))).isEqualTo("Lisboa");
            assertThat(ImportService.nameOf(labels, List.of("es"))).isIn("Lissabon", "Lisboa");
            assertThat(ImportService.nameOf(List.of(new WikidataLabel("Q1", "en", "Lisbon"),
                    new WikidataLabel("Q1", "pt", "Lisboa")), List.of("pt"))).isEqualTo("Lisbon");
            assertThat(ImportService.nameOf(List.of(new WikidataLabel("Q1", "en", "x".repeat(201))), List.of()))
                    .isNull();
        }

        @Test
        void slugs_are_lower_case_ascii_words() {
            assertThat(Slugs.of("São Jorge — Castle!")).isEqualTo("sao-jorge-castle");
            assertThat(Slugs.of("!!!")).isEqualTo("place");
            String cut = Slugs.of("a".repeat(199) + " bc");
            assertThat(cut).as("cut at 200 without a trailing hyphen").isEqualTo("a".repeat(199));
            assertThat(Slugs.of("b".repeat(250))).hasSize(200);
        }
    }
}
