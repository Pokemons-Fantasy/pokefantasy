package com.villu.pokefantasy.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.villu.pokefantasy.repository.PushNotificationPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class FirebasePushNotificationAdapter implements PushNotificationPort {

    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_JSON not set — push notifications disabled");
            return;
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
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
            log.debug("Push sent: {}/{} successful", response.getSuccessCount(), fcmTokens.size());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }
}
