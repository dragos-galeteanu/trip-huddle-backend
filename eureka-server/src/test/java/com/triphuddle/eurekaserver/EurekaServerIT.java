package com.triphuddle.eurekaserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The registry starts on its own, registers nothing itself and lists what registers with it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.enabled=false")
class EurekaServerIT {

    @Autowired TestRestTemplate http;

    @Test
    void a_service_that_registers_is_listed() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        json.setAccept(List.of(MediaType.APPLICATION_JSON));
        String instance = """
                {"instance": {"instanceId": "api-user-1", "hostName": "localhost", "app": "API-USER",
                 "ipAddr": "127.0.0.1", "status": "UP", "port": {"$": 8081, "@enabled": "true"},
                 "dataCenterInfo": {"@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                                    "name": "MyOwn"}}}""";

        ResponseEntity<Void> registered = http.exchange("/eureka/apps/API-USER", HttpMethod.POST,
                new HttpEntity<>(instance, json), Void.class);
        ResponseEntity<String> apps = http.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(json),
                String.class);

        assertThat(registered.getStatusCode().value()).isEqualTo(204);
        assertThat(apps.getBody()).contains("API-USER").doesNotContain("EUREKA-SERVER");
    }
}
