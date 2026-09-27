package com.villu.pokefantasy.it;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.migration.UserNameLowerMigration;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Consultas que dependen de Mongo de verdad: índices, búsqueda por prefijo e historial acotado. */
class QueriesIntegrationTest extends IntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private UserNameLowerMigration userNameLowerMigration;

    private void user(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setPassword("x");
        userRepository.saveUser(user);
    }

    @Test
    void usernamePrefixSearch_isCaseInsensitive_andUsesTheIndex() {
        List.of("Ash_K", "ashley", "Asher", "misty", "ASH").forEach(this::user);

        assertThat(userRepository.findByUsernamePrefix("aSh")).extracting(UserEntity::getName)
                .containsExactly("ASH", "Ash_K", "Asher", "ashley");
        assertThat(userRepository.findByUsernamePrefix("zzz")).isEmpty();

        Document explain = mongoTemplate.getDb().runCommand(new Document("explain", new Document("find", "users")
                .append("filter", new Document("nameLower", new Document("$gte", "ash").append("$lt", "ash￿")))));
        assertThat(explain.toJson()).contains("IXSCAN").contains("nameLower");
    }

    @Test
    void legacyUsersWithoutNameLower_areFoundAfterMigration() {
        mongoTemplate.getCollection("users").insertOne(new Document("name", "OldTimer").append("password", "x"));
        assertThat(userRepository.findByUsernamePrefix("old")).isEmpty();

        userNameLowerMigration.migrate();

        assertThat(userRepository.findByUsernamePrefix("old")).extracting(UserEntity::getName).containsExactly("OldTimer");
    }

    @Test
    void tradeHistory_allPendingPlusLatestResolved() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        IntStream.range(0, 30).forEach(i -> tradeRepository.save(TradeEntity.builder()
                .leagueId("l1").proposer(i % 2 == 0 ? "ash" : "misty").responder(i % 2 == 0 ? "misty" : "ash")
                .status(TradeStatus.ACCEPTED).createdAt(base).resolvedAt(base.plusSeconds(i)).build()));
        IntStream.range(0, 3).forEach(i -> tradeRepository.save(TradeEntity.builder()
                .leagueId("l1").proposer("ash").responder("brock").status(TradeStatus.PENDING).createdAt(base).build()));
        tradeRepository.save(TradeEntity.builder()
                .leagueId("l1").proposer("brock").responder("misty").status(TradeStatus.ACCEPTED)
                .createdAt(base).resolvedAt(base.plusSeconds(99)).build());

        List<TradeEntity> trades = tradeRepository.findByLeagueIdAndParticipant("l1", "ash", 10);

        assertThat(trades).hasSize(13);
        assertThat(trades).filteredOn(t -> t.getStatus() == TradeStatus.PENDING).hasSize(3);
        assertThat(trades).filteredOn(t -> t.getStatus() != TradeStatus.PENDING)
                .extracting(t -> t.getResolvedAt().getEpochSecond() - base.getEpochSecond())
                .containsExactly(29L, 28L, 27L, 26L, 25L, 24L, 23L, 22L, 21L, 20L);
    }

    @Test
    void declaredIndexesExist_includingUniqueUsername() {
        List<Document> userIndexes = mongoTemplate.getCollection("users").listIndexes().into(new java.util.ArrayList<>());
        assertThat(userIndexes).anySatisfy(index -> {
            assertThat(index.get("key", Document.class)).containsKey("name");
            assertThat(index.getBoolean("unique")).isTrue();
        });
        assertThat(mongoTemplate.getCollection("schedule").listIndexes().into(new java.util.ArrayList<>()))
                .anySatisfy(index -> assertThat(index.get("key", Document.class)).containsKey("leagueId"));

        user("ash");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> user("ash"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
