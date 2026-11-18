package com.triphuddle.api.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserDeletedContract;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserDisabledContract;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.amqp.user.UserEmailVerifiedEvent;
import com.triphuddle.amqp.user.UserRegisteredEvent;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.common.service.FirebaseUserService;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class UserApiIT {

    private static final String USERS = "/api/v1/users";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingMessageProducer published;

    @Autowired
    FirebaseUserService firebase;

    @BeforeEach
    void forgetEarlierMessages() {
        published.reset();
    }

    // --- USR-1, USR-2: registering --------------------------------------------------------------------------------

    @Test
    void registeringCreatesOneCustomerPerFirebaseAccount() throws Exception {
        String uid = "firebase-reg-" + UUID.randomUUID();
        RegisterRequest request = UserFixtures.register().build();

        JsonNode first = body(send(post(USERS + "/register"), request, firebaseUser(uid, "reg@example.com"))
                .andExpect(status().isCreated()));
        JsonNode again = body(send(post(USERS + "/register"), request, firebaseUser(uid, "reg@example.com"))
                .andExpect(status().isCreated()));

        assertThat(again.path("id")).isEqualTo(first.path("id"));
        assertThat(first.path("role").asText()).isEqualTo("Customer");
        assertThat(jdbc.queryForObject("select count(*) from users where firebase_uid = ?", Integer.class, uid))
                .isEqualTo(1);
        org.mockito.BDDMockito.then(firebase).should()
                .setUserClaims(uid, UUID.fromString(first.path("id").asText()), com.triphuddle.common.security.UserRole.Customer);
        assertThat(published.published()).extracting(Published::payload)
                .contains(new UserRegisteredEvent(UUID.fromString(first.path("id").asText())))
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(SendEmailRequest.class,
                        email -> assertThat(email.type()).isEqualTo(NotificationEmailType.EMAIL_VERIFICATION)));
    }

    @Test
    void creatingAUserAnyOtherWayIsNotAllowed() throws Exception {
        send(post(USERS), UserFixtures.register().build(), Caller.ADMIN.request())
                .andExpect(status().isMethodNotAllowed());
    }

    // --- USR-3: verifying the email through the link --------------------------------------------------------------

    @Test
    void theVerificationLinkWorksWithoutSigningInAndOnlyOnce() throws Exception {
        UUID userId = UserTestData.insertUser(jdbc, false);
        UUID token = UUID.randomUUID();
        jdbc.update("update users set email_verification_token = ? where id = ?", token, userId);

        mvc.perform(get(USERS + "/verify-email").param("token", token.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email verified")));
        mvc.perform(get(USERS + "/verify-email").param("token", token.toString()))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select email_verified from users where id = ?", Boolean.class, userId))
                .isTrue();
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOf(UserEmailVerifiedEvent.class));
    }

    // --- changing, disabling and deleting -------------------------------------------------------------------------

    @Test
    void aUserChangingTheirEmailGetsALinkToVerifyIt() throws Exception {
        UUID me = UserTestData.insertUser(jdbc, true);

        send(put(USERS), updateOf(me).email("changed-" + me + "@example.com").build(), Caller.CUSTOMER.request(me))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select email_verified from users where id = ?", Boolean.class, me)).isFalse();
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(SendEmailRequest.class, email -> {
                    assertThat(email.type()).isEqualTo(NotificationEmailType.EMAIL_VERIFICATION);
                    assertThat(email.recipientEmail()).isEqualTo("changed-" + me + "@example.com");
                }));
    }

    @Test
    void onlyTheUserOrStaffChangeAUser() throws Exception {
        UUID someone = UserTestData.insertUser(jdbc, true);

        send(put(USERS), updateOf(someone).firstName("Hacked").build(), Caller.CUSTOMER.request())
                .andExpect(status().isForbidden());
        send(put(USERS), updateOf(someone).firstName("Fixed").build(), Caller.SUPPORT.request())
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Fixed"));
    }

    @Test
    void staffDisableAUserAndTheOtherServicesAreTold() throws Exception {
        UUID someone = UserTestData.insertUser(jdbc, true);

        mvc.perform(delete(USERS + "/" + someone + "/disable").with(Caller.CUSTOMER.request()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(USERS + "/" + someone + "/disable").with(Caller.SUPPORT.request()))
                .andExpect(status().isOk());

        assertThat(published.published()).contains(new Published(new UserDisabledEvent(someone),
                UserDisabledContract.USER_DISABLED_EXCHANGE_NAME, ""));
    }

    @Test
    void aUserDeletesThemselvesWithTheirTravelDocumentsAndIsGoneAfterwards() throws Exception {
        UUID me = UserTestData.insertUser(jdbc, true);
        send(put(USERS + "/me/travel-documents"), java.util.Map.of("nationalities", java.util.List.of("RO"),
                "residencePermits", java.util.List.of()), Caller.CUSTOMER.request(me)).andExpect(status().isOk());

        mvc.perform(delete(USERS + "/" + me).with(Caller.ADMIN.request())).andExpect(status().isForbidden());
        mvc.perform(delete(USERS + "/" + me).with(Caller.CUSTOMER.request(me))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from user_travel_documents where user_id = ?", Integer.class,
                me)).isZero();
        assertThat(published.published()).contains(new Published(new UserDeletedEvent(me),
                UserDeletedContract.USER_DELETED_EXCHANGE_NAME, ""));
        mvc.perform(get(USERS + "/" + me).with(Caller.ADMIN.request())).andExpect(status().isNotFound());
    }

    // --- reading --------------------------------------------------------------------------------------------------

    @Test
    void anySignedInUserReadsTheDirectoryButNotContactDetails() throws Exception {
        UUID someone = UserTestData.insertUser(jdbc, true);

        mvc.perform(get(USERS + "/" + someone).with(Caller.CUSTOMER.request()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
        mvc.perform(get(USERS).param("page", "1").param("pageSize", "5").with(Caller.CUSTOMER.request()))
                .andExpect(status().isOk());
        mvc.perform(get(USERS + "/" + someone)).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownUserIsNotFound() throws Exception {
        mvc.perform(get(USERS + "/" + UUID.randomUUID()).with(Caller.ADMIN.request()))
                .andExpect(status().isNotFound());
    }

    // --- password reset -------------------------------------------------------------------------------------------

    @Test
    void aSignedInUserIsEmailedAResetLink() throws Exception {
        UUID me = UserTestData.insertUser(jdbc, true);
        String email = jdbc.queryForObject("select email from users where id = ?", String.class, me);
        given(firebase.generatePasswordResetLink(email)).willReturn("https://reset.example/x");

        mvc.perform(post(USERS + "/reset-password").with(jwt().jwt(j -> j.claim("email", email)
                .claim("userId", me.toString())))).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(published.published())
                .extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(SendEmailRequest.class,
                        sent -> assertThat(sent.templateData()).containsEntry("resetUrl", "https://reset.example/x"))));
    }

    // --- USR-4: the database keeps contact details unique ---------------------------------------------------------

    @Test
    void theDatabaseRefusesASecondUserWithTheSameEmailOrPhone() {
        UUID first = UserTestData.insertUser(jdbc, true);
        String email = jdbc.queryForObject("select email from users where id = ?", String.class, first);
        String phone = jdbc.queryForObject("select phone_number from users where id = ?", String.class, first);
        String insert = """
                insert into users (id, firebase_uid, email, phone_number, first_name, last_name, role,
                    email_verified, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, ?, 'A', 'B', 'Customer', false, now(), now())""";

        assertThatThrownBy(() -> jdbc.update(insert, "uid-" + UUID.randomUUID(), email, "+40000" + first.hashCode()))
                .hasMessageContaining("uk_user_email");
        assertThatThrownBy(() -> jdbc.update(insert, "uid-" + UUID.randomUUID(), "x" + first + "@e.com", phone))
                .hasMessageContaining("uk_user_phone_number");
    }

    private UpdateUserRequest.UpdateUserRequestBuilder updateOf(UUID userId) {
        return jdbc.queryForObject("select email, phone_number from users where id = ?", (rs, i) ->
                UpdateUserRequest.builder().id(userId).email(rs.getString(1)).phoneNumber(rs.getString(2))
                        .firstName("Ana").lastName("Pop"), userId);
    }

    private static RequestPostProcessor firebaseUser(String uid, String email) {
        // A Firebase token before registering: no userId claim yet.
        return jwt().jwt(j -> j.subject(uid).claim("email", email));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor caller)
            throws Exception {
        return mvc.perform(request.with(caller).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
