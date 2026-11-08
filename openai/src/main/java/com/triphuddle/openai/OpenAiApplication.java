package com.triphuddle.openai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.triphuddle.openai", "com.triphuddle.amqp"})
public class OpenAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenAiApplication.class, args);
    }
}