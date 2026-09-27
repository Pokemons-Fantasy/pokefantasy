package com.villu.pokefantasy.metrics;

import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.exception.StaleOperationException;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerCommandMetricsAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerCommandMetricsAdapter adapter = new MicrometerCommandMetricsAdapter(registry);

    @Test
    void recordsTimerPerCommandAndOutcome() {
        adapter.record("StealPokemonCommand", Duration.ofMillis(30), null);
        adapter.record("StealPokemonCommand", Duration.ofMillis(10), null);
        adapter.record("StealPokemonCommand", Duration.ofMillis(5), new IllegalStateException("window closed"));

        Timer ok = registry.get(MicrometerCommandMetricsAdapter.METRIC)
                .tags("command", "StealPokemonCommand", "outcome", "success", "exception", "none").timer();
        assertThat(ok.count()).isEqualTo(2);
        assertThat(ok.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(40);

        Timer rejected = registry.get(MicrometerCommandMetricsAdapter.METRIC)
                .tags("outcome", "rejected", "exception", "IllegalStateException").timer();
        assertThat(rejected.count()).isEqualTo(1);
    }

    @Test
    void businessRejectionsAreNotErrors() {
        assertThat(MicrometerCommandMetricsAdapter.outcome(null)).isEqualTo("success");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new IllegalArgumentException())).isEqualTo("rejected");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new StaleOperationException("x"))).isEqualTo("rejected");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new ForbiddenOperationException("x"))).isEqualTo("rejected");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new TooManyAttemptsException("x", Duration.ZERO))).isEqualTo("rejected");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new OptimisticLockingFailureException("x"))).isEqualTo("rejected");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new BadCredentialsException("x"))).isEqualTo("rejected");
        assertThat(MicrometerCommandMetricsAdapter.outcome(new RuntimeException("db down"))).isEqualTo("error");
    }
}
