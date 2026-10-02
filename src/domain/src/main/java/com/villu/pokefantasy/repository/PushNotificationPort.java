package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.PushMessage;

import java.util.List;

public interface PushNotificationPort {
    /**
     * Envía el aviso a todos los tokens (app Android y navegadores). Si la lista está vacía o Firebase
     * no está inicializado, no hace nada. No lanza excepciones.
     */
    void send(List<String> fcmTokens, PushMessage message);
}
