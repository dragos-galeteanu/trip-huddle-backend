package com.triphuddle.amqp.user;

import java.util.List;
import java.util.Map;

/**
 * Every user exchange and the queues bound to it, one per consuming service.
 * {@code RabbitMQConfig} declares each as a durable fanout exchange with these queues bound to it.
 */
public class UserExchanges {
    public static final Map<String, List<String>> BINDINGS = Map.of(
            UserDisabledContract.USER_DISABLED_EXCHANGE_NAME, List.of(
                    UserDisabledContract.USER_DISABLED_QUEUE_NAME,
                    UserDisabledContract.USER_DISABLED_PAYMENT_QUEUE_NAME),
            UserDeletedContract.USER_DELETED_EXCHANGE_NAME, List.of(
                    UserDeletedContract.USER_DELETED_QUEUE_NAME,
                    UserDeletedContract.USER_DELETED_PAYMENT_QUEUE_NAME,
                    UserDeletedContract.USER_DELETED_DESTINATION_QUEUE_NAME)
    );
}
