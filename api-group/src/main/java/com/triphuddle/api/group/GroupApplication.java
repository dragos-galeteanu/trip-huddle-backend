package com.triphuddle.api.group;

import com.triphuddle.amqp.RabbitMQConfig;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

// Mongo comes in through `common` but this service stores nothing there; left on, its health
// check would report the service down whenever no Mongo is reachable.
@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@EnableDiscoveryClient
@Import({
        GlobalExceptionHandler.class,
        RabbitMQConfig.class,
        RabbitMQMessageProducer.class
})
public class GroupApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupApplication.class, args);
    }

}
