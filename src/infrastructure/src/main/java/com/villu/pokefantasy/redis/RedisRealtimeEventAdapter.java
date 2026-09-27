package com.villu.pokefantasy.redis;

import com.villu.pokefantasy.ports.RealtimeEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reparte los eventos SSE entre instancias con Redis Pub/Sub (canal {@value #CHANNEL}): se publican en
 * Redis y cada instancia, suscrita al canal, los entrega a sus conexiones locales (también la que publicó).
 *
 * <p>Si Redis no acepta la publicación, el evento se entrega solo en esta instancia: con una única
 * instancia (Render free) se comporta igual que antes aunque Redis falle.
 */
@Component
@Slf4j
public class RedisRealtimeEventAdapter implements RealtimeEventPort, MessageListener {

    public static final String CHANNEL = "pokefantasy:realtime";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final List<Listener> listeners;

    public RedisRealtimeEventAdapter(StringRedisTemplate redisTemplate, ObjectMapper redisObjectMapper,
                                     List<Listener> listeners) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = redisObjectMapper;
        this.listeners = listeners;
    }

    @Override
    public void publish(RealtimeEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (RuntimeException e) {
            log.warn("Could not publish realtime event to Redis, delivering locally only: {}", e.getMessage());
            deliver(event);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RealtimeEvent event;
        try {
            event = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), RealtimeEvent.class);
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed realtime event: {}", e.getMessage());
            return;
        }
        deliver(event);
    }

    private void deliver(RealtimeEvent event) {
        for (Listener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                log.warn("Realtime listener {} failed: {}", listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
