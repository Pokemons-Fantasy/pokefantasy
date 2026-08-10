package com.villu.pokefantasy.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.villu.pokefantasy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
        adapter = new FirebasePushNotificationAdapter(userRepository);
    }

    @Test
    void send_notInitialized_doesNothing() {
        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(List.of("token1"), "title", "body");

            messaging.verifyNoInteractions();
            verifyNoInteractions(userRepository);
        }
    }

    @Test
    void send_emptyTokenList_doesNothing() {
        ReflectionTestUtils.setField(adapter, "initialized", true);

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(List.of(), "title", "body");

            messaging.verifyNoInteractions();
        }
    }

    @Test
    void send_nullTokenList_doesNothing() {
        ReflectionTestUtils.setField(adapter, "initialized", true);

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(null, "title", "body");

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

            adapter.send(List.of("token1"), "title", "body");

            verify(userRepository, never()).removeFcmToken(Mockito.anyString());
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

            adapter.send(List.of("stale-token"), "title", "body");

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

            adapter.send(List.of("token1"), "title", "body");

            verify(userRepository, never()).removeFcmToken(Mockito.anyString());
        }
    }

    @Test
    void send_failureWithInvalidArgumentCode_removesStaleToken() throws Exception {
        ReflectionTestUtils.setField(adapter, "initialized", true);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        SendResponse failed = mock(SendResponse.class);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(0);
        when(response.getResponses()).thenReturn(List.of(failed));

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            messaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

            adapter.send(List.of("stale-token"), "title", "body");

            verify(userRepository).removeFcmToken("stale-token");
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

            adapter.send(List.of("stale-token"), "title", "body");

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

            adapter.send(List.of("token1"), "title", "body");

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

            adapter.send(List.of("token1"), "title", "body");

            verifyNoInteractions(userRepository);
        }
    }

    @Test
    void init_envVarNotSet_leavesUninitialized() {
        adapter.init();

        try (MockedStatic<FirebaseMessaging> messaging = mockStatic(FirebaseMessaging.class)) {
            adapter.send(List.of("token1"), "title", "body");

            messaging.verifyNoInteractions();
        }
    }
}
