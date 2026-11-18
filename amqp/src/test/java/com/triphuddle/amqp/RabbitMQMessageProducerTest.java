package com.triphuddle.amqp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class RabbitMQMessageProducerTest {

    private final AmqpTemplate template = mock(AmqpTemplate.class);
    private final RabbitMQMessageProducer producer = new RabbitMQMessageProducer(template);
    private final Object payload = new Object();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsThePayloadToTheExchangeWithTheRoutingKey() {
        producer.publish(payload, "", "some.queue");

        then(template).should().convertAndSend("", "some.queue", payload);
    }

    @Test
    void waitsForTheCommitInsideATransaction() {
        TransactionSynchronizationManager.initSynchronization();

        producer.publishAfterCommit(payload, "some.exchange", "");

        then(template).should(never()).convertAndSend(anyString(), anyString(), any(Object.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        then(template).should().convertAndSend("some.exchange", "", payload);
    }

    @Test
    void neverSendsWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        producer.publishAfterCommit(payload, "", "some.queue");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        then(template).shouldHaveNoInteractions();
    }

    @Test
    void sendsAtOnceOutsideATransaction() {
        producer.publishAfterCommit(payload, "", "some.queue");

        then(template).should().convertAndSend("", "some.queue", payload);
    }
}
