package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserDeviceRegisteredEvent}.
 */
public class UserDeviceRegisteredContract {
    public static final String USER_DEVICE_REGISTERED_QUEUE_NAME = "user.device.registered.queue";
    public static final String USER_DEVICE_REGISTERED_DLQ_NAME = "user.device.registered.dlq";
}
