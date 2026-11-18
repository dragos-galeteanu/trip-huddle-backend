package com.triphuddle.notification.messaging;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.NotificationPushContract;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.amqp.user.UserDeviceRegisteredEvent;
import com.triphuddle.amqp.user.UserDeviceUnregisteredEvent;
import com.triphuddle.notification.entity.DeviceToken;
import com.triphuddle.notification.repository.DeviceTokenRepository;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;

/** Emails, pushes and device tokens end to end, through a real RabbitMQ and MongoDB. */
@SpringBootTest
@ActiveProfiles("it")
@Import({MongoContainerConfig.class, NotificationIT.Outside.class})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationIT {

    // Set as properties, as the amqp module's own broker test does: the shared config's connection factory
    // dials what spring.rabbitmq names.
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Outside {
        @Bean @Primary JavaMailSender mailSender() { return Mockito.mock(JavaMailSender.class); }
        @Bean FirebaseMessaging firebaseMessaging() { return Mockito.mock(FirebaseMessaging.class); }
    }

    @Autowired @Qualifier("amqpTemplate") AmqpTemplate amqp;
    @Autowired JavaMailSender mailSender;
    @Autowired FirebaseMessaging firebase;
    @Autowired DeviceTokenRepository tokens;
    @Autowired UserDeviceEventListener deviceEvents;
    @SpyBean EmailDlqListener emailDeadLetters;
    @SpyBean PushDlqListener pushDeadLetters;

    @BeforeEach
    void reset() {
        Mockito.reset(mailSender, firebase);
        tokens.deleteAll();
        given(mailSender.createMimeMessage()).willAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    static SendEmailRequest email() {
        return new SendEmailRequest(NotificationEmailType.WELCOME, "ana@example.com",
                Map.of("firstName", "Ana", "greetingName", "Ana", "appUrl", "https://app.example"), null);
    }

    @Test
    void a_device_token_belongs_to_the_last_user_who_registered_it_until_they_unregister_it() {
        UUID ana = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        deviceEvents.onDeviceRegistered(new UserDeviceRegisteredEvent(ana, "token-1", "ios"));
        deviceEvents.onDeviceRegistered(new UserDeviceRegisteredEvent(bob, "token-1", "android"));

        List<DeviceToken> held = tokens.findByUserIdIn(List.of(ana, bob));
        assertThat(held).singleElement().satisfies(token -> {
            assertThat(token.getUserId()).isEqualTo(bob);
            assertThat(token.getPlatform()).isEqualTo("android");
        });

        deviceEvents.onDeviceUnregistered(new UserDeviceUnregisteredEvent(ana, "token-1"));
        assertThat(tokens.findByToken("token-1")).isPresent();
        deviceEvents.onDeviceUnregistered(new UserDeviceUnregisteredEvent(bob, "token-1"));
        assertThat(tokens.findByToken("token-1")).isEmpty();
    }

    @Test
    void an_email_request_on_the_queue_is_sent() {
        amqp.convertAndSend("", NotificationEmailContract.EMAIL_QUEUE_NAME, email());

        then(mailSender).should(timeout(10_000)).send(any(MimeMessage.class));
    }

    @Test
    void an_smtp_failure_is_retried_three_times_then_dead_lettered() {
        willThrow(new MailSendException("SMTP down")).given(mailSender).send(any(MimeMessage.class));

        amqp.convertAndSend("", NotificationEmailContract.EMAIL_QUEUE_NAME, email());

        then(emailDeadLetters).should(timeout(20_000)).onDeadLetter(any(SendEmailRequest.class));
        then(mailSender).should(times(3)).send(any(MimeMessage.class));
    }

    @Test
    void a_message_that_cannot_be_built_is_dead_lettered_without_retrying() {
        given(mailSender.createMimeMessage()).willThrow(new IllegalStateException("broken"));

        amqp.convertAndSend("", NotificationEmailContract.EMAIL_QUEUE_NAME, email());

        then(emailDeadLetters).should(timeout(20_000)).onDeadLetter(any(SendEmailRequest.class));
        then(mailSender).should(times(1)).createMimeMessage();
    }

    @Test
    void a_push_request_on_the_queue_reaches_the_users_devices() throws Exception {
        UUID ana = UUID.randomUUID();
        deviceEvents.onDeviceRegistered(new UserDeviceRegisteredEvent(ana, "token-ana", "ios"));
        BatchResponse ok = Mockito.mock(BatchResponse.class);
        given(ok.getResponses()).willReturn(List.of());
        given(firebase.sendEachForMulticast(any(MulticastMessage.class))).willReturn(ok);

        amqp.convertAndSend("", NotificationPushContract.PUSH_QUEUE_NAME,
                new SendPushRequest(List.of(ana), "Hi", "There", Map.of()));

        then(firebase).should(timeout(10_000)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void a_push_that_fails_for_good_is_dead_lettered_without_retrying() throws Exception {
        UUID ana = UUID.randomUUID();
        deviceEvents.onDeviceRegistered(new UserDeviceRegisteredEvent(ana, "token-ana", "ios"));
        given(firebase.sendEachForMulticast(any(MulticastMessage.class))).willThrow(new IllegalArgumentException("bad"));

        amqp.convertAndSend("", NotificationPushContract.PUSH_QUEUE_NAME,
                new SendPushRequest(List.of(ana), "Hi", "There", Map.of()));

        then(pushDeadLetters).should(timeout(20_000)).onDeadLetter(any(SendPushRequest.class));
        then(firebase).should(times(1)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void a_failing_firebase_is_retried_three_times_then_dead_lettered() throws Exception {
        UUID ana = UUID.randomUUID();
        deviceEvents.onDeviceRegistered(new UserDeviceRegisteredEvent(ana, "token-ana", "ios"));
        given(firebase.sendEachForMulticast(any(MulticastMessage.class)))
                .willThrow(com.google.firebase.messaging.FirebaseErrors.unavailable());

        amqp.convertAndSend("", NotificationPushContract.PUSH_QUEUE_NAME,
                new SendPushRequest(List.of(ana), "Hi", "There", Map.of()));

        then(pushDeadLetters).should(timeout(20_000)).onDeadLetter(any(SendPushRequest.class));
        then(firebase).should(times(3)).sendEachForMulticast(any(MulticastMessage.class));
    }
}
