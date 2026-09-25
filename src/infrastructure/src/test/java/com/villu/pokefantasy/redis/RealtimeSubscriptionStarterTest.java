package com.villu.pokefantasy.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSubscriptionStarterTest {

    private final RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
    private final RealtimeSubscriptionStarter starter = new RealtimeSubscriptionStarter(container);

    @Test
    void notRunning_startsIt() {
        when(container.isRunning()).thenReturn(false);

        starter.ensureSubscribed();

        verify(container).start();
    }

    @Test
    void alreadyRunning_doesNothing() {
        when(container.isRunning()).thenReturn(true);

        starter.ensureSubscribed();

        verify(container, never()).start();
    }

    @Test
    void redisDown_swallowsAndStopsForNextRetry() {
        when(container.isRunning()).thenReturn(false);
        doThrow(new RedisConnectionFailureException("down")).when(container).start();

        assertThatCode(starter::ensureSubscribed).doesNotThrowAnyException();
        verify(container).stop();
    }

    @Test
    void redisDownAndStopAlsoFails_stillSwallows() {
        when(container.isRunning()).thenReturn(false);
        doThrow(new RedisConnectionFailureException("down")).when(container).start();
        doThrow(new IllegalStateException("half started")).when(container).stop();

        assertThatCode(starter::ensureSubscribed).doesNotThrowAnyException();
    }
}
