package com.triphuddle.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.notification.entity.DeviceToken;
import com.triphuddle.notification.repository.DeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends a push notification to every registered device of its recipients through Firebase Cloud Messaging.
 */
@Slf4j
@Service
public class PushService {

    // The most tokens FCM accepts in one multicast.
    private static final int MAX_TOKENS_PER_BATCH = 500;

    // FCM's answer for a token that will never work again: the app was uninstalled or the token rotated.
    private static final Set<MessagingErrorCode> STALE_TOKEN_ERRORS =
            Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT);

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    public PushService(ObjectProvider<FirebaseMessaging> firebaseMessagingProvider,
                       DeviceTokenRepository deviceTokenRepository) {
        // Absent when firebase.enabled is false; pushes are then dropped with a log line.
        this.firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * Send the notification to every device of the recipients, forgetting the tokens FCM rejects for good.
     *
     * @param request the recipients and content of the notification
     * @throws FirebaseMessagingException if FCM cannot be reached, which is worth retrying
     */
    public void send(SendPushRequest request) throws FirebaseMessagingException {
        if (firebaseMessaging == null) {
            log.warn("Push notifications are disabled; dropping push [recipients={}]", request.recipientUserIds().size());
            return;
        }

        List<String> tokens = deviceTokenRepository.findByUserIdIn(request.recipientUserIds()).stream()
                .map(DeviceToken::getToken)
                .toList();
        if (tokens.isEmpty()) {
            log.info("No devices registered for push [recipients={}]", request.recipientUserIds().size());
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(request.title())
                .setBody(request.body())
                .build();
        Map<String, String> data = request.data() == null ? Map.of() : request.data();

        List<String> staleTokens = new ArrayList<>();
        int delivered = 0;
        for (int start = 0; start < tokens.size(); start += MAX_TOKENS_PER_BATCH) {
            List<String> batch = tokens.subList(start, Math.min(start + MAX_TOKENS_PER_BATCH, tokens.size()));
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .setNotification(notification)
                    .putAllData(data)
                    .build();

            // Only a failure of the whole call is thrown, and retried; single tokens failing are not,
            // since a retry would push again to every device that already got it.
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            delivered += response.getSuccessCount();

            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);
                if (!sendResponse.isSuccessful() && sendResponse.getException() != null
                        && STALE_TOKEN_ERRORS.contains(sendResponse.getException().getMessagingErrorCode())) {
                    staleTokens.add(batch.get(i));
                }
            }
        }

        if (!staleTokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(staleTokens);
        }
        log.info("Push sent [devices={}, delivered={}, staleTokensRemoved={}]",
                tokens.size(), delivered, staleTokens.size());
    }
}
