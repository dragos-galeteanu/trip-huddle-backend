package com.triphuddle.api.platform;

import com.triphuddle.amqp.RabbitMQConfig;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
@Import({
        GlobalExceptionHandler.class,
        RabbitMQConfig.class,
        RabbitMQMessageProducer.class
})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }

}
