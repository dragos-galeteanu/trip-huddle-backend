package com.triphuddle.api.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.user.UserDeviceRegisteredEvent;
import com.triphuddle.amqp.user.UserDeviceUnregisteredEvent;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Travel documents (USR-6) and push devices (USR-5), which always belong to the caller.
 */
@UserIntegrationTest
class OwnDataApiIT {

    private static final String DOCUMENTS = "/api/v1/users/me/travel-documents";
    private static final String DEVICES = "/api/v1/users/me/devices";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    RecordingMessageProducer published;

    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void forgetEarlierMessages() {
        published.reset();
    }

    @Test
    void replacesAndReadsBackMyDocumentsAndAnnouncesThem() throws Exception {
        String nextYear = LocalDate.now().plusYears(1).toString();

        documents(List.of("ro", "MD"), List.of(Map.of("countryCode", "DE", "kind", "TEMPORARY",
                "validUntil", nextYear))).andExpect(status().isOk());
        documents(List.of("IT"), List.of()).andExpect(status().isOk());

        mvc.perform(get(DOCUMENTS).with(Caller.CUSTOMER.request(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalities.length()").value(1))
                .andExpect(jsonPath("$.nationalities[0]").value("IT"))
                .andExpect(jsonPath("$.residencePermits.length()").value(0));
        assertThat(published.published()).extracting(Published::payload)
                .filteredOn(UserTravelDocumentsUpdatedEvent.class::isInstance).hasSize(2);
    }

    @Test
    void keepsWithinFourNationalitiesAndTenPermits() throws Exception {
        documents(List.of("RO", "MD", "IT", "FR", "ES"), List.of()).andExpect(status().isBadRequest());
        documents(List.of(), Collections.nCopies(11, Map.of("countryCode", "DE", "kind", "PERMANENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUnknownCountryAndAPermitForMyOwnCountry() throws Exception {
        documents(List.of("ZZ"), List.of()).andExpect(status().isUnprocessableEntity());
        documents(List.of("RO"), List.of(Map.of("countryCode", "RO", "kind", "PERMANENT")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void needsASignedInUser() throws Exception {
        mvc.perform(get(DOCUMENTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void passesDeviceTokensOnToNotification() throws Exception {
        send(DEVICES, Map.of("token", "fcm-1", "platform", "ANDROID")).andExpect(status().isAccepted());
        send(DEVICES + "/unregister", Map.of("token", "fcm-1")).andExpect(status().isAccepted());
        send(DEVICES, Map.of("token", "fcm-2", "platform", "TOASTER")).andExpect(status().isBadRequest());

        assertThat(published.published()).extracting(Published::payload).containsExactly(
                new UserDeviceRegisteredEvent(me, "fcm-1", "ANDROID"), new UserDeviceUnregisteredEvent(me, "fcm-1"));
    }

    private ResultActions documents(List<String> nationalities, List<Map<String, String>> permits) throws Exception {
        return mvc.perform(put(DOCUMENTS).with(Caller.CUSTOMER.request(me)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("nationalities", nationalities, "residencePermits", permits))));
    }

    private ResultActions send(String path, Object body) throws Exception {
        RequestPostProcessor caller = Caller.CUSTOMER.request(me);
        return mvc.perform(post(path).with(caller).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }
}
