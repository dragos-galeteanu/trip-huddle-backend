package com.triphuddle.testsupport;

import com.triphuddle.testsupport.containers.PostgresContainerConfig;
import com.triphuddle.testsupport.messaging.RecordingMessageProducerConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A full application context with MockMvc, against Postgres in a container, with messages recorded rather than sent.
 * Services that also use Mongo add {@code @Import(MongoContainerConfig.class)}; profile "it" turns off the outside world.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import({PostgresContainerConfig.class, RecordingMessageProducerConfig.class})
public @interface IntegrationTest {
}
