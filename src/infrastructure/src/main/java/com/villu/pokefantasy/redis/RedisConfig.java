package com.villu.pokefantasy.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJacksonJsonRedisSerializer(redisObjectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJacksonJsonRedisSerializer(redisObjectMapper));
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Suscripción al canal de eventos en tiempo real (una conexión dedicada por instancia). No arranca con
     * la app: si Redis no respondiera, el arranque fallaría. La arranca {@link RealtimeSubscriptionStarter}.
     */
    @Bean
    public RedisMessageListenerContainer realtimeEventListenerContainer(RedisConnectionFactory connectionFactory,
                                                                        RedisRealtimeEventAdapter realtimeEventAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(realtimeEventAdapter, new ChannelTopic(RedisRealtimeEventAdapter.CHANNEL));
        container.setAutoStartup(false);
        return container;
    }
}
