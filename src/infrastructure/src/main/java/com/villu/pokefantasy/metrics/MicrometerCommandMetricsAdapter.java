package com.villu.pokefantasy.metrics;

import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.ports.CommandMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Timer {@value #METRIC} por comando: {@code command} (p. ej. StealPokemonCommand), {@code outcome}
 * (success / rejected / error) y {@code exception}. Su {@code count} es la métrica de negocio (robos,
 * trades, picks… hechos y rechazados). Se consulta en {@code /actuator/metrics/pokefantasy.commands}.
 */
@Component
public class MicrometerCommandMetricsAdapter implements CommandMetricsPort {

    static final String METRIC = "pokefantasy.commands";

    private final MeterRegistry registry;

    public MicrometerCommandMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(String command, Duration duration, Throwable failure) {
        Timer.builder(METRIC)
                .description("Comandos ejecutados por el mediator")
                .tag("command", command)
                .tag("outcome", outcome(failure))
                .tag("exception", failure == null ? "none" : failure.getClass().getSimpleName())
                .register(registry)
                .record(duration);
    }

    /** Un rechazo de negocio (4xx) no es lo mismo que un fallo (5xx): así se pueden alertar por separado. */
    static String outcome(Throwable failure) {
        if (failure == null) return "success";
        if (failure instanceof IllegalArgumentException        // 400
                || failure instanceof IllegalStateException     // 409 (incluye StaleOperationException)
                || failure instanceof ForbiddenOperationException
                || failure instanceof TooManyAttemptsException
                || failure instanceof OptimisticLockingFailureException
                || failure instanceof AuthenticationException) {
            return "rejected";
        }
        return "error";
    }
}
