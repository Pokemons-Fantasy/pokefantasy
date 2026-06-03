package com.villu.pokefantasy.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class FirebasePushNotificationAdapter implements PushNotificationPort {

    private final UserRepository userRepository;
    private volatile boolean initialized = false;

    public FirebasePushNotificationAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_JSON not set — push notifications disabled");
            return;
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(
                            "https://www.googleapis.com/auth/firebase.messaging",
                            "https://www.googleapis.com/auth/cloud-platform");
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
            log.info("Firebase initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage(), e);
        }
    }

    @Override
    public void send(List<String> fcmTokens, String title, String body) {
        if (!initialized || fcmTokens == null || fcmTokens.isEmpty()) return;
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .addAllTokens(fcmTokens)
                    .build();
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Push sent: {}/{} successful for title='{}'", response.getSuccessCount(), fcmTokens.size(), title);
            cleanupStaleTokens(fcmTokens, response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }

    private void cleanupStaleTokens(List<String> fcmTokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (!r.isSuccessful()) {
                MessagingErrorCode code = r.getException() != null ? r.getException().getMessagingErrorCode() : null;
                String staleToken = fcmTokens.get(i);
                Throwable root = r.getException();
                while (root != null && root.getCause() != null) root = root.getCause();
                log.warn("FCM token failed (errorCode={}, msg={}, rootCause={}): {}...", code,
                        r.getException() != null ? r.getException().getMessage() : "none",
                        root != null ? root.getClass().getSimpleName() + ": " + root.getMessage() : "none",
                        staleToken.substring(0, Math.min(20, staleToken.length())));
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT
                        || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                    userRepository.removeFcmToken(staleToken);
                }
            }
        }
    }
}
