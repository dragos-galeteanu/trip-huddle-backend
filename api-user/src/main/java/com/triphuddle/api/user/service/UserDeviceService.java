package com.triphuddle.api.user.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.user.UserDeviceRegisteredContract;
import com.triphuddle.amqp.user.UserDeviceRegisteredEvent;
import com.triphuddle.amqp.user.UserDeviceUnregisteredContract;
import com.triphuddle.amqp.user.UserDeviceUnregisteredEvent;
import com.triphuddle.api.user.dto.RegisterUserDeviceRequest;
import com.triphuddle.api.user.dto.UnregisterUserDeviceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Passes the caller's push devices on to the notification service, which stores the tokens and sends pushes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeviceService {

    private final RabbitMQMessageProducer messageProducer;

    /**
     * Register a device of the user for push notifications.
     *
     * @param userId the ID of the user
     * @param request the token and platform of the device
     */
    public void register(UUID userId, RegisterUserDeviceRequest request) {
        messageProducer.publish(
                new UserDeviceRegisteredEvent(userId, request.getToken(), request.getPlatform().name()),
                "",
                UserDeviceRegisteredContract.USER_DEVICE_REGISTERED_QUEUE_NAME
        );
        log.info("Event published for user device registration [userId={}, platform={}]",
                userId, request.getPlatform());
    }

    /**
     * Stop push notifications on a device of the user.
     *
     * @param userId the ID of the user
     * @param request the token of the device
     */
    public void unregister(UUID userId, UnregisterUserDeviceRequest request) {
        messageProducer.publish(
                new UserDeviceUnregisteredEvent(userId, request.getToken()),
                "",
                UserDeviceUnregisteredContract.USER_DEVICE_UNREGISTERED_QUEUE_NAME
        );
        log.info("Event published for user device unregistration [userId={}]", userId);
    }
}
