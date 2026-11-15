package com.triphuddle.openai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Entry point of the OpenAI service: the AI worker that runs other services' text and embedding jobs from amqp.
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.triphuddle.openai", "com.triphuddle.amqp"})
// Not a component, so the scan above doesn't find it.
@Import(RabbitMQMessageProducer.class)
public class OpenAiApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OpenAiApplication.class, args);
    }
}