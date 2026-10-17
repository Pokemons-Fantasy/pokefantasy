package com.villu.pokefantasy.ports;

import java.time.Duration;

/**
 * Contador de intentos por clave dentro de una ventana de tiempo: logins fallidos por usuario o IP y
 * registros por IP ({@code register-ip:<ip>}). La política (cuántos bloquean) vive en
 * {@code LoginUserCommandHandler} y {@code CreateUserCommandHandler}.
 */
public interface LoginAttemptPort {

    /** Fallos registrados para {@code key} en la ventana actual (0 si no hay ninguno). */
    long failureCount(String key);

    /** Suma un fallo a {@code key}; la ventana empieza con el primer fallo y dura {@code window}. */
    void recordFailure(String key, Duration window);

    /** Olvida los fallos de {@code key} (p. ej. tras un login correcto). */
    void clearFailures(String key);

    /** Recuerda {@code key} (usuario + IP con un login correcto) durante {@code ttl}. */
    void markTrusted(String key, Duration ttl);

    /** Si {@code key} tuvo un login correcto dentro de su {@code ttl}. */
    boolean isTrusted(String key);
}
