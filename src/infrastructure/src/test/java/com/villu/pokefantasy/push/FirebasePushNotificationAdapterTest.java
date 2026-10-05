package com.villu.pokefantasy.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebasePushNotificationAdapterTest {

    @Mock private UserRepository userRepository;
    @Mock private FirebaseMessaging firebaseMessaging;

    private FirebasePushNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FirebasePushNotificationAdapter(userRepository, "https://pokefantasy.netlify.app");
    }

    @Test
    void send_notInitialized_doesNothing() {
        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            messaging.verifyNoInteractions();
            verifyNoInteractions(userRepository);
        }
    }

    @Test
    void send_emptyTokenList_doesNothing() {
        ReflectionTestUtils.setField(adapter, "initialized", true);

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(List.of(), new PushMessage("title", "body", null, null));

            messaging.verifyNoInteractions();
        }
    }

    @Test
    void send_nullTokenList_doesNothing() {
        ReflectionTestUtils.setField(adapter, "initialized", true);

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(null, new PushMessage("title", "body", null, null));

            messaging.verifyNoInteractions();
        }
    }

    @Test
    void send_allSuccessful_doesNotRemoveAnyToken() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        SendResponse ok = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(1);
        when(response.getResponses()).thenReturn(List.of(ok));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            verify(userRepository, never()).removeFcmToken(Mockito.anyString());
        }
    }

    @Test
    void send_insideTransaction_defersUntilAfterCommit() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        SendResponse ok = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(List.of(ok));

        TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));
            verify(firebaseMessaging, never()).sendEachForMulticast(any(MulticastMessage.class));

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(firebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void send_unexpectedRuntimeException_isLoggedNotPropagated() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                    .thenThrow(new IllegalStateException("network down"));

            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            verifyNoInteractions(userRepository);
        }
    }

    @Test
    void send_failureWithUnregisteredCode_removesStaleToken() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(0);
        when(response.getResponses()).thenReturn(List.of(failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("stale-token"), new PushMessage("title", "body", null, null));

            verify(userRepository).removeFcmToken("stale-token");
        }
    }

    @Test
    void send_failureWithTransientCode_doesNotRemoveToken() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(0);
        when(response.getResponses()).thenReturn(List.of(failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            verify(userRepository, never()).removeFcmToken(Mockito.anyString());
        }
    }

    @Test
    void send_invalidArgumentWhileAnotherTokenSucceeded_removesThatToken() throws Exception {
        // El mensaje era válido (a otro token le llegó): el INVALID_ARGUMENT es del token
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        SendResponse ok = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(1);
        when(response.getResponses()).thenReturn(List.of(ok, failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("good-token", "bad-token"), new PushMessage("title", "body", null, null));

            verify(userRepository).removeFcmToken("bad-token");
            verify(userRepository, never()).removeFcmToken("good-token");
        }
    }

    @Test
    void send_invalidArgumentForEveryToken_keepsThem() throws Exception {
        // Ningún token lo recibió: puede ser el mensaje, no se borran los tokens de todos
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("Invalid value at 'message.webpush.headers'");
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(0);
        when(response.getResponses()).thenReturn(List.of(failed, failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("token1", "token2"), new PushMessage("title", "body", null, null));

            verify(userRepository, never()).removeFcmToken(Mockito.anyString());
        }
    }

    @Test
    void send_failureWithSenderIdMismatchCode_removesStaleToken() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.SENDER_ID_MISMATCH);
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(0);
        when(response.getResponses()).thenReturn(List.of(failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("stale-token"), new PushMessage("title", "body", null, null));

            verify(userRepository).removeFcmToken("stale-token");
        }
    }

    @Test
    void send_failureWithNullException_doesNotRemoveToken() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(null);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(0);
        when(response.getResponses()).thenReturn(List.of(failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            verify(userRepository, never()).removeFcmToken(Mockito.anyString());
        }
    }

    @Test
    void send_sendEachForMulticastThrows_doesNotPropagate() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenThrow(exception);

            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            verifyNoInteractions(userRepository);
        }
    }

    @Test
    void init_envVarNotSet_leavesUninitialized() {
        adapter.init();

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(List.of("token1"), new PushMessage("title", "body", null, null));

            messaging.verifyNoInteractions();
        }
    }

    @Test
    void buildMessage_withPath_addsWebLinkTagAndIcon() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.draftTurn("l1", "¡Te toca en el draft!", "Liga Kanto · ronda 1", Duration.ofSeconds(120)),
                "https://pokefantasy.netlify.app");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        assertThat(webpush).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(webpush, "data");
        assertThat(data).containsEntry("link", "https://pokefantasy.netlify.app/leagues/l1/draft")
                .containsEntry("tag", "draft-turn-l1");
        Object fcmOptions = ReflectionTestUtils.getField(webpush, "fcmOptions");
        assertThat(ReflectionTestUtils.getField(fcmOptions, "link")).isEqualTo("https://pokefantasy.netlify.app/leagues/l1/draft");
        @SuppressWarnings("unchecked")
        Map<String, Object> notification = (Map<String, Object>) ReflectionTestUtils.getField(webpush, "notification");
        assertThat(notification).containsEntry("title", "¡Te toca en el draft!")
                .containsEntry("tag", "draft-turn-l1")
                .containsEntry("icon", "https://pokefantasy.netlify.app/icons/icon-192.png");
    }

    @Test
    void buildMessage_withoutPath_isAndroidOnlyAsBefore() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                new PushMessage("title", "body", null, null), "https://pokefantasy.netlify.app");

        assertThat(ReflectionTestUtils.getField(message, "webpushConfig")).isNull();
        assertThat(ReflectionTestUtils.getField(message, "notification")).isNotNull();
    }

    @Test
    void buildMessage_trailingSlashInWebUrl_doesNotDoubleIt() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.teams("l1", "Te han robado un Pokémon", "texto"),
                FirebasePushNotificationAdapter.webOrigin("https://pokefantasy.netlify.app/"));

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(webpush, "data");
        assertThat(data).containsEntry("link", "https://pokefantasy.netlify.app/leagues/l1/teams").doesNotContainKey("tag");
    }

    @Test
    void buildMessage_httpWebUrl_keepsLinkInDataButSkipsFcmOptions() {
        // WebpushFcmOptions.withLink exige HTTPS: con una URL local no debe romper el envío
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.teams("l1", "t", "b"), "http://localhost:5173");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        assertThat(ReflectionTestUtils.getField(webpush, "fcmOptions")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(webpush, "data");
        assertThat(data).containsEntry("link", "http://localhost:5173/leagues/l1/teams");
    }

    @Test
    void buildMessage_urgentWithTtl_setsWebHeadersAndAndroidPriority() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.draftTurn("l1", "¡Te toca en el draft!", "texto", Duration.ofMinutes(2)),
                "https://pokefantasy.netlify.app");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) ReflectionTestUtils.getField(webpush, "headers");
        assertThat(headers).containsEntry("TTL", "120").containsEntry("Urgency", "high");
        Object android = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(ReflectionTestUtils.getField(android, "ttl")).isEqualTo("120s");
        assertThat(ReflectionTestUtils.getField(android, "priority")).isEqualTo("high");
    }

    @Test
    void buildMessage_ttlWithoutUrgency_onlyExpires() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.window("l1", "steal", "t", "b", Duration.ofHours(2)), "https://pokefantasy.netlify.app");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) ReflectionTestUtils.getField(webpush, "headers");
        assertThat(headers).containsEntry("TTL", "7200").doesNotContainKey("Urgency");
        Object android = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(ReflectionTestUtils.getField(android, "ttl")).isEqualTo("7200s");
        assertThat(ReflectionTestUtils.getField(android, "priority")).isNull();
    }

    @Test
    void buildMessage_urgentWithoutTtl_onlyRaisesPriority() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                new PushMessage("t", "b", null, null, null, true), "https://pokefantasy.netlify.app");

        Object android = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(ReflectionTestUtils.getField(android, "ttl")).isNull();
        assertThat(ReflectionTestUtils.getField(android, "priority")).isEqualTo("high");
    }

    @Test
    void buildMessage_withoutTtlNorUrgency_leavesFcmDefaults() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.teams("l1", "t", "b"), "https://pokefantasy.netlify.app");

        assertThat(ReflectionTestUtils.getField(message, "androidConfig")).isNull();
        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) ReflectionTestUtils.getField(webpush, "headers");
        assertThat(headers).isNullOrEmpty();
    }

    @Test
    void pushMessage_negativeTtl_becomesZero() {
        assertThat(PushMessage.window("l1", "steal", "t", "b", Duration.ofMinutes(-5)).ttl()).isEqualTo(Duration.ZERO);
    }

    @Test
    void webOrigin_acceptsOriginsAndDropsTrailingSlash() {
        assertThat(FirebasePushNotificationAdapter.webOrigin("https://pokefantasy.netlify.app/"))
                .isEqualTo("https://pokefantasy.netlify.app");
        assertThat(FirebasePushNotificationAdapter.webOrigin(" http://localhost:5173 ")).isEqualTo("http://localhost:5173");
    }

    @Test
    void webOrigin_rejectsAnythingThatIsNotAnOrigin() {
        for (String bad : new String[] {null, "", "pokefantasy.netlify.app", "ftp://pokefantasy.netlify.app",
                "https://pokefantasy.netlify.app/leagues", "https://pokefantasy.netlify.app?x=1",
                "https://pokefantasy.netlify.app#a", "https://user@pokefantasy.netlify.app", "https://", "https://exa mple.com"}) {
            assertThatThrownBy(() -> FirebasePushNotificationAdapter.webOrigin(bad))
                    .as(String.valueOf(bad))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WEB_URL");
        }
    }

    @Test
    void constructor_withInvalidWebUrl_failsAtStartup() {
        assertThatThrownBy(() -> new FirebasePushNotificationAdapter(userRepository, "https://pokefantasy.netlify.app/app"))
                .isInstanceOf(IllegalStateException.class);
    }
}
