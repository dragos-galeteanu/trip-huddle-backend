package com.triphuddle.api.user;

import com.triphuddle.amqp.RabbitMQConfig;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.common.config.FirebaseConfig;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import com.triphuddle.common.service.FirebaseUserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the user service, which owns users, their profiles and their preferences.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@Import({
        GlobalExceptionHandler.class,
        FirebaseConfig.class,
        FirebaseUserService.class,
        RabbitMQConfig.class,
        RabbitMQMessageProducer.class
})
public class UserApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }

}