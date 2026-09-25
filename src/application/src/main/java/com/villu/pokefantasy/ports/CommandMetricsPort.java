package com.villu.pokefantasy.ports;

import java.time.Duration;

/**
 * Métricas de negocio: cada comando del mediator (robos, trades, picks, resultados…) con su duración y
 * resultado. Contar comandos por tipo y resultado da las métricas de negocio sin tocar cada handler.
 */
public interface CommandMetricsPort {

    /** @param failure {@code null} si el comando terminó bien; si no, la excepción que lanzó. */
    void record(String command, Duration duration, Throwable failure);
}
