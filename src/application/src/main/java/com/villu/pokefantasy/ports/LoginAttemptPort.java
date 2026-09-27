package com.villu.pokefantasy.ports;

import java.time.Duration;

/**
 * Contador de intentos de login fallidos por clave (usuario o IP) dentro de una ventana de tiempo.
 * La política (cuántos fallos bloquean) vive en {@code LoginUserCommandHandler}.
 */
public interface LoginAttemptPort {

    /** Fallos registrados para {@code key} en la ventana actual (0 si no hay ninguno). */
    long failureCount(String key);

    /** Suma un fallo a {@code key}; la ventana empieza con el primer fallo y dura {@code window}. */
    void recordFailure(String key, Duration window);

    /** Olvida los fallos de {@code key} (p. ej. tras un login correcto). */
    void clearFailures(String key);
}
