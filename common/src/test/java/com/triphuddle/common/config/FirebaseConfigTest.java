package com.triphuddle.common.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class FirebaseConfigTest {

    private final FirebaseApp app = mock(FirebaseApp.class);

    @Test
    void initializesTheSdkOnceWithApplicationDefaultCredentials() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("token", null));
        try (MockedStatic<FirebaseApp> firebase = mockStatic(FirebaseApp.class);
             MockedStatic<GoogleCredentials> google = mockStatic(GoogleCredentials.class)) {
            firebase.when(FirebaseApp::getApps).thenReturn(List.of());
            firebase.when(() -> FirebaseApp.initializeApp(any(FirebaseOptions.class))).thenReturn(app);
            google.when(GoogleCredentials::getApplicationDefault).thenReturn(credentials);

            FirebaseConfig config = config();

            firebase.verify(() -> FirebaseApp.initializeApp(any(FirebaseOptions.class)));
            assertThat(ReflectionTestUtils.getField(config, "firebaseApp")).isSameAs(app);
        }
    }

    @Test
    void reusesAnSdkAnotherContextInitialized() throws Exception {
        try (MockedStatic<FirebaseApp> firebase = mockStatic(FirebaseApp.class)) {
            firebase.when(FirebaseApp::getApps).thenReturn(List.of(app));
            firebase.when(FirebaseApp::getInstance).thenReturn(app);

            FirebaseConfig config = config();

            firebase.verify(() -> FirebaseApp.initializeApp(any(FirebaseOptions.class)), never());
            assertThat(ReflectionTestUtils.getField(config, "firebaseApp")).isSameAs(app);
        }
    }

    @Test
    void exposesTheAuthOfItsApp() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        try (MockedStatic<FirebaseApp> firebase = mockStatic(FirebaseApp.class);
             MockedStatic<FirebaseAuth> firebaseAuth = mockStatic(FirebaseAuth.class)) {
            firebase.when(FirebaseApp::getApps).thenReturn(List.of(app));
            firebase.when(FirebaseApp::getInstance).thenReturn(app);
            firebaseAuth.when(() -> FirebaseAuth.getInstance(app)).thenReturn(auth);

            assertThat(config().firebaseAuth()).isSameAs(auth);
        }
    }

    private static FirebaseConfig config() throws Exception {
        FirebaseConfig config = new FirebaseConfig();
        ReflectionTestUtils.setField(config, "projectId", "test-project");
        config.initialize();
        return config;
    }
}
