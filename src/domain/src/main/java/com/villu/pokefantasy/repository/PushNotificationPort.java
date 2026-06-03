package com.villu.pokefantasy.repository;

import java.util.List;

public interface PushNotificationPort {
    /**
     * Envía notificación a todos los tokens. Si la lista está vacía o Firebase
     * no está inicializado, no hace nada. No lanza excepciones.
     */
    void send(List<String> fcmTokens, String title, String body);
}
