package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserDeviceUnregisteredEvent}.
 */
public class UserDeviceUnregisteredContract {
    public static final String USER_DEVICE_UNREGISTERED_QUEUE_NAME = "user.device.unregistered.queue";
    public static final String USER_DEVICE_UNREGISTERED_DLQ_NAME = "user.device.unregistered.dlq";
}
