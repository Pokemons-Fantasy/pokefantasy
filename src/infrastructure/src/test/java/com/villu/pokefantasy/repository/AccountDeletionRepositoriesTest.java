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
    void tradeCancelPending_setsCancelledOnlyOnThisLeaguesPendingTradesOfTheUser() {
        Instant at = Instant.parse("2026-10-18T10:00:00Z");

        new TradeRepositoryImpl(mongoTemplate).cancelPending("l1", "ash", at);

        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(TradeEntity.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q.get("leagueId")).isEqualTo("l1");
        assertThat(q.get("status")).isEqualTo(TradeStatus.PENDING);
        assertThat(q.getList("$or", Document.class))
                .containsExactlyInAnyOrder(new Document("proposer", "ash"), new Document("responder", "ash"));
        assertThat(update.getValue().getUpdateObject())
                .isEqualTo(new Document("$set", new Document("status", TradeStatus.CANCELLED).append("resolvedAt", at)));
    }

    @Test
    void tradeAnonymizeUser_rewritesProposerAndResponderInThatLeague() {
        new TradeRepositoryImpl(mongoTemplate).anonymizeUser("l1", "ash", "Usuario eliminado");

        verify(mongoTemplate, times(2)).updateMulti(query.capture(), update.capture(), eq(TradeEntity.class));
        assertRewrites("proposer", "responder");
    }

    @Test
    void activityDeleteByLeagueId_removesTheLeaguesEvents() {
        new ActivityEventMongoRepository(mongoTemplate).deleteByLeagueId("l1");

        verify(mongoTemplate).remove(query.capture(), eq(ActivityEventEntity.class), eq("activity_events"));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
    }

    @Test
    void activityAnonymizeUser_rewritesActorAndTargetInThatLeague() {
        new ActivityEventMongoRepository(mongoTemplate).anonymizeUser("l1", "ash", "Usuario eliminado");

        verify(mongoTemplate, times(2))
                .updateMulti(query.capture(), update.capture(), eq(ActivityEventEntity.class), eq("activity_events"));
        assertRewrites("actorUsername", "targetUsername");
    }

    private void assertRewrites(String... fields) {
        List<Query> queries = query.getAllValues();
        List<Update> updates = update.getAllValues();
        for (int i = 0; i < fields.length; i++) {
            assertThat(queries.get(i).getQueryObject())
                    .isEqualTo(new Document("leagueId", "l1").append(fields[i], "ash"));
            assertThat(updates.get(i).getUpdateObject())
                    .isEqualTo(new Document("$set", new Document(fields[i], "Usuario eliminado")));
        }
    }
}
