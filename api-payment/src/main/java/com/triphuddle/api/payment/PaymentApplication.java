package com.triphuddle.api.payment;

import com.triphuddle.amqp.RabbitMQConfig;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import com.triphuddle.stripe.config.StripeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the payment service, which takes payments for license subscriptions through Stripe.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(PaymentProperties.class)
@EnableScheduling
@Import({
        GlobalExceptionHandler.class,
        RabbitMQConfig.class,
        RabbitMQMessageProducer.class,
        StripeConfig.class
})
public class PaymentApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
