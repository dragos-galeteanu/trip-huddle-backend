package com.triphuddle.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Firebase Cloud Messaging client from the application default credentials; off when {@code firebase.enabled} is false.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseMessagingConfig {

    /**
     * The messaging client of this service's Firebase app.
     *
     * @param projectId the Google Cloud project to send through
     * @return the messaging client
     * @throws IOException if the application default credentials cannot be read
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(@Value("${firebase.project-id}") String projectId) throws IOException {
        // FirebaseApp keeps a JVM-wide registry, and a second initialization of the default app fails.
        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId)
                    .build();
            app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized for push notifications [projectId={}]", projectId);
        } else {
            app = FirebaseApp.getInstance();
        }
        return FirebaseMessaging.getInstance(app);
    }
}
