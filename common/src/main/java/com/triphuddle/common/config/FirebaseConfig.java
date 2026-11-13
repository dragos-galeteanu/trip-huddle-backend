package com.triphuddle.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Bootstraps the Firebase Admin SDK from the application default credentials, once per JVM.
 * Skipped when {@code firebase.enabled} is {@code false}, so tests can start without Firebase.
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {

    /**
     * Google Cloud project the Admin SDK should operate against.
     */
    @Value("${firebase.project-id}")
    private String projectId;

    /**
     * The initialized Firebase application, backing the {@link #firebaseAuth()} bean.
     */
    private FirebaseApp firebaseApp;

    /**
     * Initializes the Firebase Admin SDK before any bean depending on it is created.
     *
     * @throws IOException if the application default credentials cannot be resolved or read
     */
    @PostConstruct
    public void initialize() throws IOException {
        // FirebaseApp keeps a JVM-wide registry and a second initialization would fail, so an
        // existing instance is reused when the context is rebuilt, as it is between test classes.
        if (FirebaseApp.getApps().isEmpty()) {
            // Resolve credentials from the environment instead of a bundled service account key
            GoogleCredentials credentials;
            credentials = GoogleCredentials.getApplicationDefault();
            log.info("Firebase Admin SDK using application default credentials");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();

            firebaseApp = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized successfully for project: {}", projectId);
        } else {
            // Another context in this JVM already initialized the SDK, so reuse its instance
            firebaseApp = FirebaseApp.getInstance();
            log.info("Firebase Admin SDK already initialized");
        }
    }

    /**
     * Exposes {@link FirebaseAuth} as a bean, so that callers inject it instead of using the
     * static {@code FirebaseAuth.getInstance()}.
     *
     * @return the {@link FirebaseAuth} instance bound to this service's {@link FirebaseApp}
     */
    @Bean
    public FirebaseAuth firebaseAuth() {
        // As a bean it can be mocked in tests, and Spring guarantees initialize() has run first.
        return FirebaseAuth.getInstance(firebaseApp);
    }

}
