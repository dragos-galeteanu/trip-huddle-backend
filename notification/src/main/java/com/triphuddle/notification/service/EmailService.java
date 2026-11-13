package com.triphuddle.notification.service;

import com.triphuddle.amqp.notification.EmailAttachment;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

/**
 * Renders an email from its Thymeleaf template and sends it over SMTP.
 */
@Slf4j
@Service
public class EmailService {

    private static final String DISPLAY_NAME = "TripHuddle";

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        ITemplateEngine templateEngine,
                        @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = DISPLAY_NAME + " <" + fromAddress + ">";
    }

    /**
     * Render the template for the request's type and send it, with any attachments.
     *
     * @param request the email to send
     * @throws MessagingException if the message cannot be built
     */
    public void send(SendEmailRequest request) throws MessagingException {
        String templateName = resolveTemplate(request.type());
        String subject = resolveSubject(request.type());

        Context context = new Context();
        context.setVariables(request.templateData());
        String html = templateEngine.process("email/" + templateName, context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(request.recipientEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            List<EmailAttachment> attachments = request.attachments();
            if (attachments != null) {
                for (EmailAttachment attachment : attachments) {
                    DataSource ds = new ByteArrayDataSource(attachment.content(), attachment.contentType());
                    helper.addAttachment(attachment.filename(), ds);
                }
            }
            mailSender.send(message);
            log.info("Email sent [type={}, to={}, attachments={}]", request.type(), request.recipientEmail(),
                    attachments != null ? attachments.size() : 0);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email [type=" + request.type() + "]", e);
        }
    }

    private String resolveTemplate(NotificationEmailType type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> "email-verification";
            case PASSWORD_RESET -> "password-reset";
            case WELCOME -> "welcome";
            case PROFILE_PICTURE_UPDATED -> "profile-picture-updated";
            case GROUP_INVITATION -> "group-invitation";
        };
    }

    private String resolveSubject(NotificationEmailType type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> "Verify your TripHuddle email address";
            case PASSWORD_RESET -> "Reset your TripHuddle password";
            case WELCOME -> "Welcome to TripHuddle!";
            case PROFILE_PICTURE_UPDATED -> "Your TripHuddle profile pictures were updated";
            case GROUP_INVITATION -> "You have been invited to a group on TripHuddle";
        };
    }
}
