package com.triphuddle.api.user.api;

import com.triphuddle.common.service.FirebaseUserService;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.mockito.Mockito.mock;

/**
 * api-user's integration tests: Postgres and Mongo in containers, and Firebase replaced by a mock, which the tests
 * can stub. Every test class uses the same set, so they share one application context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@IntegrationTest
@Import({MongoContainerConfig.class, UserIntegrationTest.FirebaseStub.class})
public @interface UserIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class FirebaseStub {

        @Bean
        FirebaseUserService firebaseUserService() {
            return mock(FirebaseUserService.class);
        }
    }
}
