package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Métodos de borrado y anonimización que usa el borrado de cuenta (L2). */
class AccountDeletionRepositoriesTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    private final ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);

    @Test
    void userDeleteByUsername_removesByName() {
        new UserRepositoryImpl(mongoTemplate).deleteByUsername("ash");

        verify(mongoTemplate).remove(query.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("name", "ash"));
    }

    @Test
    void draftFindAllByLeagueId_loadsWholeDocuments() {
        // Sin proyección: el borrado de cuenta guarda estos drafts y una proyección perdería los picks.
        new DraftRepositoryImpl(mongoTemplate).findAllByLeagueId("l1");

        verify(mongoTemplate).find(query.capture(), eq(DraftEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
        assertThat(query.getValue().getFieldsObject()).isEmpty();
    }

    @Test
    void leagueDeleteById_removesThatLeague() {
        new LeagueRepositoryImpl(mongoTemplate).deleteById("l1");

        verify(mongoTemplate).remove(query.capture(), eq(LeagueEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("_id", "l1"));
    }

    @Test
    void scheduleDeleteByLeagueId_removesTheLeaguesSchedule() {
        new ScheduleRepositoryImpl(mongoTemplate).deleteByLeagueId("l1");

        verify(mongoTemplate).remove(query.capture(), eq(ScheduleEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
    }

    @Test
    void closedListDeleteAllByLeagueId_removesTheLeaguesList() {
        new ClosedListRepositoryImpl(mongoTemplate).deleteAllByLeagueId("l1");

        verify(mongoTemplate).remove(query.capture(), eq(ClosedListEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
    }

    @Test
    void tradeDeleteByLeagueId_removesTheLeaguesTrades() {
        new TradeRepositoryImpl(mongoTemplate).deleteByLeagueId("l1");

        verify(mongoTemplate).remove(query.capture(), eq(TradeEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
    }

    @Test
    void tradeCancelPending_setsCancelledOnEveryPendingTradeOfTheUser() {
        // En todas sus ligas, también en las que ya dejó (salir o ser expulsado no los cancela).
        Instant at = Instant.parse("2026-10-18T10:00:00Z");

        new TradeRepositoryImpl(mongoTemplate).cancelPending("ash", at);

        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(TradeEntity.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q.containsKey("leagueId")).isFalse();
        assertThat(q.get("status")).isEqualTo(TradeStatus.PENDING);
        assertThat(q.getList("$or", Document.class))
                .containsExactlyInAnyOrder(new Document("proposer", "ash"), new Document("responder", "ash"));
        assertThat(update.getValue().getUpdateObject())
                .isEqualTo(new Document("$set", new Document("status", TradeStatus.CANCELLED).append("resolvedAt", at)));
    }

    @Test
    void tradeAnonymizeUser_rewritesProposerAndResponderEverywhere() {
        new TradeRepositoryImpl(mongoTemplate).anonymizeUser("ash", GONE);

        verify(mongoTemplate, times(2)).updateMulti(query.capture(), update.capture(), eq(TradeEntity.class));
        assertFlatRewrites("proposer", "responder");
    }

    @Test
    void activityDeleteByLeagueId_removesTheLeaguesEvents() {
        new ActivityEventMongoRepository(mongoTemplate).deleteByLeagueId("l1");

        verify(mongoTemplate).remove(query.capture(), eq(ActivityEventEntity.class), eq("activity_events"));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
    }

    @Test
    void activityAnonymizeUser_rewritesActorAndTargetEverywhere() {
        new ActivityEventMongoRepository(mongoTemplate).anonymizeUser("ash", GONE);

        verify(mongoTemplate, times(2))
                .updateMulti(query.capture(), update.capture(), eq(ActivityEventEntity.class), eq("activity_events"));
        assertFlatRewrites("actorUsername", "targetUsername");
    }

    @Test
    void leagueAnonymizeUser_rewritesCreatedBy() {
        new LeagueRepositoryImpl(mongoTemplate).anonymizeUser("ash", GONE);

        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(LeagueEntity.class));
        assertFlatRewrites("createdBy");
    }

    @Test
    void closedListAnonymizeUser_rewritesNominatedBy() {
        new ClosedListRepositoryImpl(mongoTemplate).anonymizeUser("ash", GONE);

        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(ClosedListEntity.class));
        assertFlatRewrites("nominatedBy");
    }

    @Test
    void scheduleAnonymizeUser_rewritesBothPlayersAndTheWinnerInEveryMatch() {
        new ScheduleRepositoryImpl(mongoTemplate).anonymizeUser("ash", GONE);

        verify(mongoTemplate, times(3)).updateMulti(query.capture(), update.capture(), eq(ScheduleEntity.class));
        assertArrayRewrites(
                new String[] {"jornadas.matches.player1", "jornadas.matches.player2", "jornadas.matches.winnerUsername"},
                new String[] {"jornadas.$[].matches.$[m].player1", "jornadas.$[].matches.$[m].player2",
                        "jornadas.$[].matches.$[m].winnerUsername"},
                new String[] {"m.player1", "m.player2", "m.winnerUsername"});
    }

    @Test
    void draftAnonymizeUser_rewritesPicksHistoryAndTurnOrder() {
        new DraftRepositoryImpl(mongoTemplate).anonymizeUser("ash", GONE);

        verify(mongoTemplate, times(3)).updateMulti(query.capture(), update.capture(), eq(DraftEntity.class));
        assertArrayRewrites(
                new String[] {"picks.username", "draftHistory.username", "turnOrder"},
                new String[] {"picks.$[p].username", "draftHistory.$[p].username", "turnOrder.$[t]"},
                new String[] {"p.username", "p.username", "t"});
    }

    private static final String GONE = "Usuario eliminado";

    private void assertFlatRewrites(String... fields) {
        List<Query> queries = query.getAllValues();
        List<Update> updates = update.getAllValues();
        for (int i = 0; i < fields.length; i++) {
            assertThat(queries.get(i).getQueryObject()).isEqualTo(new Document(fields[i], "ash"));
            assertThat(updates.get(i).getUpdateObject().get("$set", Document.class))
                    .isEqualTo(new Document(fields[i], GONE));
        }
    }

    private void assertArrayRewrites(String[] queried, String[] set, String[] filtered) {
        List<Query> queries = query.getAllValues();
        List<Update> updates = update.getAllValues();
        for (int i = 0; i < queried.length; i++) {
            assertThat(queries.get(i).getQueryObject()).isEqualTo(new Document(queried[i], "ash"));
            assertThat(updates.get(i).getUpdateObject().get("$set", Document.class)).isEqualTo(new Document(set[i], GONE));
            assertThat(updates.get(i).getArrayFilters()).extracting(f -> f.asDocument())
                    .containsExactly(new Document(filtered[i], "ash"));
        }
    }
}
