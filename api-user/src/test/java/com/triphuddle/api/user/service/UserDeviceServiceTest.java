package com.triphuddle.api.user.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.user.UserDeviceRegisteredContract;
import com.triphuddle.amqp.user.UserDeviceRegisteredEvent;
import com.triphuddle.amqp.user.UserDeviceUnregisteredContract;
import com.triphuddle.amqp.user.UserDeviceUnregisteredEvent;
import com.triphuddle.api.user.dto.RegisterUserDeviceRequest;
import com.triphuddle.api.user.dto.UnregisterUserDeviceRequest;
import com.triphuddle.api.user.entity.DevicePlatform;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * USR-5: device tokens go to notification, which owns them.
 */
class UserDeviceServiceTest {

    private final RabbitMQMessageProducer producer = mock(RabbitMQMessageProducer.class);
    private final UserDeviceService service = new UserDeviceService(producer);
    private final UUID userId = UUID.randomUUID();

    @Test
    void passesARegisteredTokenOnWithItsPlatform() {
        service.register(userId, RegisterUserDeviceRequest.builder().token("fcm-token").platform(DevicePlatform.IOS)
                .build());

        then(producer).should().publish(new UserDeviceRegisteredEvent(userId, "fcm-token", "IOS"), "",
                UserDeviceRegisteredContract.USER_DEVICE_REGISTERED_QUEUE_NAME);
    }

    @Test
    void passesAnUnregisteredTokenOn() {
        service.unregister(userId, UnregisterUserDeviceRequest.builder().token("fcm-token").build());

        then(producer).should().publish(new UserDeviceUnregisteredEvent(userId, "fcm-token"), "",
                UserDeviceUnregisteredContract.USER_DEVICE_UNREGISTERED_QUEUE_NAME);
    }
}
