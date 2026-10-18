package com.villu.pokefantasy.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class FirebasePushNotificationAdapter implements PushNotificationPort {

    private final UserRepository userRepository;
    /** Origen de la web (sin barra final) para los enlaces y el icono de los avisos en el navegador. */
    private final String webUrl;
    private volatile boolean initialized = false;

    public FirebasePushNotificationAdapter(UserRepository userRepository,
                                           @Value("${pokefantasy.web-url:https://pokefantasy.netlify.app}") String webUrl) {
        this.userRepository = userRepository;
        this.webUrl = webOrigin(webUrl);
        log.info("Avisos push en la web: enlaces a {}", this.webUrl);
    }

    /**
     * Valida {@code WEB_URL} al arrancar: http(s), con dominio y sin ruta (se quita la barra final). Mal
     * configurada, los avisos llevarían enlaces rotos sin que nadie lo note; así falla el arranque y Render
     * sigue sirviendo la instancia anterior.
     */
    static String webOrigin(String webUrl) {
        String trimmed = webUrl == null ? "" : webUrl.strip();
        String origin = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException e) {
            throw invalidWebUrl(webUrl);
        }
        boolean http = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());
        if (!http || uri.getHost() == null || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw invalidWebUrl(webUrl);
        }
        return origin;
    }

    private static IllegalStateException invalidWebUrl(String webUrl) {
        return new IllegalStateException("WEB_URL (pokefantasy.web-url) debe ser el origen de la web, p. ej. "
                + "https://pokefantasy.netlify.app, sin ruta: '" + webUrl + "'");
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
    public void send(List<String> fcmTokens, PushMessage message) {
        if (!initialized || fcmTokens == null || fcmTokens.isEmpty()) return;
        List<String> tokens = List.copyOf(fcmTokens);
        // Dentro de un comando transaccional se envía tras el commit: si la transacción
        // se aborta o se reintenta, no llega una notificación de algo que no ha ocurrido.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(tokens, message);
                }
            });
        } else {
            doSend(tokens, message);
        }
    }

    /**
     * Mensaje para la app y los navegadores. Caducidad y urgencia van a Android ({@code ttl}, prioridad alta) y
     * a la web (cabeceras {@code TTL} y {@code Urgency}). Con ruta, añade la parte web: enlace absoluto (en
     * {@code fcmOptions} si es HTTPS, que es lo que exige Firebase, y siempre en {@code data.link} para el
     * service worker), etiqueta e icono. {@code origin} es el de {@link #webOrigin}.
     */
    static MulticastMessage buildMessage(List<String> tokens, PushMessage message, String origin) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .addAllTokens(tokens);
        if (message.ttl() != null || message.urgent()) {
            AndroidConfig.Builder android = AndroidConfig.builder();
            if (message.ttl() != null) android.setTtl(message.ttl().toMillis());
            if (message.urgent()) android.setPriority(AndroidConfig.Priority.HIGH);
            builder.setAndroidConfig(android.build());
        }
        if (message.path() == null) {
            return builder.build();
        }
        String link = origin + message.path();
        WebpushNotification.Builder notification = WebpushNotification.builder()
                .setTitle(message.title())
                .setBody(message.body())
                .setIcon(origin + "/icons/icon-192.png");
        WebpushConfig.Builder webpush = WebpushConfig.builder().putData("link", link);
        if (message.ttl() != null) webpush.putHeader("TTL", String.valueOf(message.ttl().toSeconds()));
        if (message.urgent()) webpush.putHeader("Urgency", "high");
        if (message.tag() != null) {
            // renotify: un aviso con la misma etiqueta sustituye al anterior y vuelve a sonar
            notification.setTag(message.tag()).setRenotify(true);
            webpush.putData("tag", message.tag());
        }
        if (link.startsWith("https://")) {
            webpush.setFcmOptions(WebpushFcmOptions.withLink(link));
        }
        return builder.setWebpushConfig(webpush.setNotification(notification.build()).build()).build();
    }

    private void doSend(List<String> fcmTokens, PushMessage pushMessage) {
        try {
            MulticastMessage message = buildMessage(fcmTokens, pushMessage, webUrl);
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Push sent: {}/{} successful for title='{}'", response.getSuccessCount(), fcmTokens.size(), pushMessage.title());
            cleanupStaleTokens(fcmTokens, response);
        } catch (FirebaseMessagingException | RuntimeException e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Quita los tokens que ya no sirven. {@code INVALID_ARGUMENT} lo da tanto un token mal formado como un
     * mensaje inválido: solo se trata como token malo si a otro token del mismo envío le fue bien (el mensaje
     * era válido); si no, se borrarían los tokens de todos los destinatarios por un fallo nuestro.
     */
    private void cleanupStaleTokens(List<String> fcmTokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        boolean messageAccepted = response.getSuccessCount() > 0;
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (!r.isSuccessful()) {
                MessagingErrorCode code = r.getException() != null ? r.getException().getMessagingErrorCode() : null;
                String staleToken = fcmTokens.get(i);
                log.warn("FCM token failed (errorCode={}): {}...", code,
                        staleToken.substring(0, Math.min(20, staleToken.length())));
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH
                        || (code == MessagingErrorCode.INVALID_ARGUMENT && messageAccepted)) {
                    userRepository.removeFcmToken(staleToken);
                } else if (code == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.error("FCM rechazó el aviso con INVALID_ARGUMENT y ningún token lo recibió: se conservan "
                            + "los tokens, puede ser el mensaje: {}", r.getException().getMessage());
                }
            }
        }
    }
}
