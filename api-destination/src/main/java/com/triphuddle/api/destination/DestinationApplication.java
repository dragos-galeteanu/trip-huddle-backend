package com.triphuddle.api.destination;

import com.triphuddle.api.destination.config.SocialProperties;
import com.triphuddle.api.destination.config.SearchProperties;
import com.triphuddle.amqp.RabbitMQConfig;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.config.DestinationProperties;
import com.triphuddle.api.destination.config.TripProperties;
import com.triphuddle.api.destination.config.VisaProperties;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the destination service: places, their categories, and later AI travel content, trip plans and
 * visa guidance.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({DestinationProperties.class, ContentProperties.class, TripProperties.class,
        VisaProperties.class, SocialProperties.class, SearchProperties.class})
@EnableScheduling
@Import({
        GlobalExceptionHandler.class,
        RabbitMQConfig.class,
        RabbitMQMessageProducer.class
})
public class DestinationApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DestinationApplication.class, args);
    }
}
