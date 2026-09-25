package com.villu.pokefantasy.it;

import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Base de los tests de integración: la app completa (HTTP, mediator, transacciones, seguridad) contra
 * Mongo en replica set y Redis reales en Docker. Los contenedores se arrancan una vez para toda la
 * ejecución y cada test empieza con la base de datos y Redis vacíos.
 *
 * <p>Sin Docker, los tests se saltan ({@code disabledWithoutDocker}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        // Jobs periódicos fuera de juego: cada test dispara lo que necesita.
        "draft.turn-timeout-check-ms=3600000",
        "pokemon-cache.check-interval-ms=3600000",
        // Las conexiones SSE abiertas harían esperar 30 s al apagado ordenado.
        "server.shutdown=immediate",
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    // Replica set: sin él Mongo no admite transacciones (y en Testcontainers 2 hay que pedirlo).
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7").withReplicaSet();
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        if (!MONGO.isRunning()) MONGO.start();
        if (!REDIS.isRunning()) REDIS.start();
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("pokefantasy"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** Sin red: la caché de Pokémon no se carga en los tests. */
    @MockitoBean
    protected PokemonApiAdapter pokemonApiAdapter;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @LocalServerPort
    protected int port;

    @BeforeEach
    void cleanState() {
        // Vacía los documentos pero conserva colecciones e índices (p. ej. el único de users.name).
        for (String collection : mongoTemplate.getCollectionNames()) {
            mongoTemplate.getCollection(collection).deleteMany(new org.bson.Document());
        }
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    protected ApiClient client() {
        return new ApiClient(port);
    }
}
