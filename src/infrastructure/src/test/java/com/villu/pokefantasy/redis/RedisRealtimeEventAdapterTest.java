package com.villu.pokefantasy.redis;

import com.villu.pokefantasy.ports.RealtimeEventPort.Audience;
import com.villu.pokefantasy.ports.RealtimeEventPort.Listener;
import com.villu.pokefantasy.ports.RealtimeEventPort.RealtimeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisRealtimeEventAdapterTest {

    private static final RealtimeEvent STEAL =
            new RealtimeEvent(Audience.USER, "misty", "steal", "{\"pokemonName\":\"pikachu\"}");

    private StringRedisTemplate redisTemplate;
    private Listener leagueListener;
    private Listener userListener;
    private RedisRealtimeEventAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        leagueListener = mock(Listener.class);
        userListener = mock(Listener.class);
        adapter = new RedisRealtimeEventAdapter(redisTemplate, new ObjectMapper(), List.of(leagueListener, userListener));
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage(RedisRealtimeEventAdapter.CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void publish_sendsToChannelAndDoesNotDeliverDirectly() {
        adapter.publish(STEAL);

        verify(redisTemplate).convertAndSend(eq(RedisRealtimeEventAdapter.CHANNEL), anyString());
        // La entrega llega por la suscripción (también en esta instancia), no dos veces.
        verifyNoInteractions(leagueListener, userListener);
    }

    @Test
    void publishedMessage_roundTripsToEveryListener() {
        adapter.publish(STEAL);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(RedisRealtimeEventAdapter.CHANNEL), json.capture());

        adapter.onMessage(message(json.getValue()), null);

        verify(leagueListener).onEvent(STEAL);
        verify(userListener).onEvent(STEAL);
    }

    @Test
    void publish_redisDown_deliversLocally() {
        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        adapter.publish(STEAL);

        verify(leagueListener).onEvent(STEAL);
        verify(userListener).onEvent(STEAL);
    }

    @Test
    void malformedMessage_ignored() {
        assertThatCode(() -> adapter.onMessage(message("not json"), null)).doesNotThrowAnyException();
        verifyNoInteractions(leagueListener, userListener);
    }

    @Test
    void failingListener_doesNotStopTheOthers() {
        doThrow(new IllegalStateException("boom")).when(leagueListener).onEvent(any());

        adapter.onMessage(message("{\"audience\":\"LEAGUE\",\"target\":\"l1\",\"name\":\"draft-updated\",\"data\":\"{}\"}"), null);

        ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(userListener).onEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new RealtimeEvent(Audience.LEAGUE, "l1", "draft-updated", "{}"));
    }
}
