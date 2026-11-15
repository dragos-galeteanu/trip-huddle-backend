package com.triphuddle.api.chat;

import com.triphuddle.amqp.RabbitMQConfig;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.api.chat.config.ChatProperties;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Entry point of the chat service, which owns private and group conversations and their messages.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(ChatProperties.class)
@Import({
        GlobalExceptionHandler.class,
        RabbitMQConfig.class,
        RabbitMQMessageProducer.class
})
public class ChatApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }

}
