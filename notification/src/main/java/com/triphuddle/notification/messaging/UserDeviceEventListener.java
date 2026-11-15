package com.triphuddle.notification.messaging;

import com.triphuddle.amqp.user.UserDeviceRegisteredContract;
import com.triphuddle.amqp.user.UserDeviceRegisteredEvent;
import com.triphuddle.amqp.user.UserDeviceUnregisteredContract;
import com.triphuddle.amqp.user.UserDeviceUnregisteredEvent;
import com.triphuddle.notification.service.DeviceTokenService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the push devices in step with what api-user reports about users' devices.
 */
@Slf4j
@Component
@AllArgsConstructor
public class UserDeviceEventListener {

    private final DeviceTokenService deviceTokenService;

    /**
     * Store a newly registered device.
     *
     * @param event the user and their device
     */
    @RabbitListener(
            queues = UserDeviceRegisteredContract.USER_DEVICE_REGISTERED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void onDeviceRegistered(UserDeviceRegisteredEvent event) {
        log.info("Received user device registered [userId={}, platform={}]", event.userId(), event.platform());
        deviceTokenService.register(event.userId(), event.token(), event.platform());
    }

    /**
     * Forget an unregistered device.
     *
     * @param event the user and their device
     */
    @RabbitListener(
            queues = UserDeviceUnregisteredContract.USER_DEVICE_UNREGISTERED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void onDeviceUnregistered(UserDeviceUnregisteredEvent event) {
        log.info("Received user device unregistered [userId={}]", event.userId());
        deviceTokenService.unregister(event.userId(), event.token());
    }
}
