package com.triphuddle.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.notification.entity.DeviceToken;
import com.triphuddle.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PushServiceTest {

    final FirebaseMessaging firebase = mock(FirebaseMessaging.class);
    final DeviceTokenRepository tokens = mock(DeviceTokenRepository.class);
    final UUID ana = UUID.randomUUID();

    PushService service(FirebaseMessaging messaging) {
        @SuppressWarnings("unchecked")
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(messaging);
        return new PushService(provider, tokens);
    }

    void devices(int count) {
        List<DeviceToken> held = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DeviceToken token = new DeviceToken();
            token.setUserId(ana);
            token.setToken("token-" + i);
            held.add(token);
        }
        given(tokens.findByUserIdIn(List.of(ana))).willReturn(held);
    }

    static SendResponse ok() {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(true);
        return response;
    }

    static SendResponse failed(MessagingErrorCode code) {
        SendResponse response = mock(SendResponse.class);
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        given(error.getMessagingErrorCode()).willReturn(code);
        given(response.getException()).willReturn(error);
        return response;
    }

    static BatchResponse batch(SendResponse... responses) {
        int successes = (int) List.of(responses).stream().filter(SendResponse::isSuccessful).count();
        BatchResponse batch = mock(BatchResponse.class);
        given(batch.getResponses()).willReturn(List.of(responses));
        given(batch.getSuccessCount()).willReturn(successes);
        return batch;
    }

    @Test
    void tokens_that_will_never_work_again_are_forgotten_and_others_kept() throws Exception {
        devices(4);
        // Built first: Mockito can't stub these mocks while the multicast's stubbing is open.
        BatchResponse answer = batch(ok(), failed(MessagingErrorCode.UNREGISTERED),
                failed(MessagingErrorCode.UNAVAILABLE), failed(MessagingErrorCode.INVALID_ARGUMENT));
        given(firebase.sendEachForMulticast(any(MulticastMessage.class))).willReturn(answer);

        service(firebase).send(new SendPushRequest(List.of(ana), "Hi", "There", Map.of("type", "CHAT_MESSAGE")));

        then(tokens).should().deleteByTokenIn(List.of("token-1", "token-3"));
    }

    @Test
    void more_than_500_devices_go_in_several_multicasts() throws Exception {
        devices(501);
        BatchResponse answer = batch(ok());
        given(firebase.sendEachForMulticast(any(MulticastMessage.class))).willReturn(answer);

        service(firebase).send(new SendPushRequest(List.of(ana), "Hi", "There", null));

        then(firebase).should(times(2)).sendEachForMulticast(any(MulticastMessage.class));
        then(tokens).should(never()).deleteByTokenIn(any());
    }

    @Test
    void nothing_is_sent_without_devices_or_without_firebase() throws Exception {
        devices(0);
        service(firebase).send(new SendPushRequest(List.of(ana), "Hi", "There", null));
        service(null).send(new SendPushRequest(List.of(ana), "Hi", "There", null));

        then(firebase).should(never()).sendEachForMulticast(any(MulticastMessage.class));
    }
}
