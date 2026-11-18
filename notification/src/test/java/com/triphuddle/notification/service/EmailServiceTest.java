package com.triphuddle.notification.service;

import com.triphuddle.amqp.notification.EmailAttachment;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/** Every email type renders from its template, with the values its senders put in. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailServiceTest {

    final JavaMailSender mailSender = mock(JavaMailSender.class);
    final EmailService service = new EmailService(mailSender, engine(), "no-reply@example.com");

    static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** One value for every variable any template reads, so each renders all its branches' text. */
    static Map<String, Object> data() {
        Map<String, Object> data = new HashMap<>();
        for (String url : List.of("verificationUrl", "resetUrl", "acceptUrl", "rejectUrl", "planUrl", "invoiceUrl",
                "subscriptionUrl", "ticketUrl", "appUrl", "helpUrl")) {
            data.put(url, "https://app.example/" + url);
        }
        data.putAll(Map.of("firstName", "Ana", "greetingName", "Ana", "expiryHours", 24, "hours", 24,
                "expiryMinutes", 30, "minutes", 30, "groupName", "Lisbon trip", "expiresInDays", 7,
                "isAdminRole", true, "planTitle", "Long weekend"));
        data.putAll(Map.of("dayCount", 3, "startDate", "2026-12-01", "amount", "€9.99", "isGroup", false,
                "licenseName", "Explorer", "nextAttemptDate", "2026-12-03", "isAutoRenew", true, "isGranted", false,
                "periodEnd", "2027-01-01", "endsAt", "2027-01-01"));
        data.putAll(Map.of("isGroupRemoved", false, "price", "€9.99", "renewsAt", "2027-01-01",
                "requestedAt", "2026-11-17 10:00", "requestedIp", "203.0.113.9", "coverPictureChanged", true,
                "profilePictureChanged", true, "reference", "TH-42", "subject", "Cannot pay", "isOutbound", false));
        return data;
    }

    MimeMessage sent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        then(mailSender).should().send(captor.capture());
        return captor.getValue();
    }

    static String html(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(NotificationEmailType.class)
    void each_type_renders_its_template_with_its_subject(NotificationEmailType type) throws Exception {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));

        service.send(new SendEmailRequest(type, "ana@example.com", data(), null));

        MimeMessage message = sent();
        assertThat(message.getSubject()).contains("TripHuddle");
        assertThat(message.getFrom()[0].toString()).isEqualTo("TripHuddle <no-reply@example.com>");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(html(message)).contains(String.valueOf(java.time.Year.now().getValue()));
        // An invitee may have no account yet, so their invitation greets nobody by name.
        if (type != NotificationEmailType.GROUP_INVITATION) {
            assertThat(html(message)).contains("Ana");
        }
    }

    @Test
    void each_template_renders_without_its_optional_values() throws Exception {
        for (NotificationEmailType type : NotificationEmailType.values()) {
            given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
            Map<String, Object> sparse = new HashMap<>(data());
            sparse.keySet().removeAll(List.of("firstName", "helpUrl", "invoiceUrl", "nextAttemptDate", "expiresInDays",
                    "requestedAt", "requestedIp"));

            service.send(new SendEmailRequest(type, "ana@example.com", sparse, List.of()));
        }
        then(mailSender).should(org.mockito.Mockito.times(NotificationEmailType.values().length))
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void attachments_go_along() throws Exception {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));

        service.send(new SendEmailRequest(NotificationEmailType.LICENSE_PAYMENT_SUCCEEDED, "ana@example.com", data(),
                List.of(new EmailAttachment("invoice.pdf", new byte[] {1, 2, 3}, "application/pdf"))));

        MimeMessage message = sent();
        message.saveChanges();
        Multipart parts = (Multipart) message.getContent();
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < parts.getCount(); i++) {
            BodyPart part = parts.getBodyPart(i);
            names.add(part.getFileName());
        }
        assertThat(names).contains("invoice.pdf");
    }

    @Test
    void an_address_that_cannot_be_parsed_fails_the_send_and_nothing_goes_out() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(new SendEmailRequest(
                        NotificationEmailType.LICENSE_PAYMENT_SUCCEEDED, "ana@@example..com <", data())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send email [type=LICENSE_PAYMENT_SUCCEEDED]")
                .hasCauseInstanceOf(jakarta.mail.MessagingException.class);
        then(mailSender).should(org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }
}
