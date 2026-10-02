package com.villu.pokefantasy.migration;

import com.mongodb.client.result.UpdateResult;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClearStaleTiersMigrationTest {

    private static DraftEntity draft(String leagueId, DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(leagueId);
        draft.setStatus(status);
        return draft;
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrate_keepsTiersOnlyWhereTheLatestDraftUsesThem() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        // Más reciente primero: en "kanto" manda el cancelado; "johto" sigue en temporada; "hoenn" en preparación
        when(mongoTemplate.find(any(Query.class), eq(DraftEntity.class))).thenReturn(List.of(
                draft("kanto", DraftStatus.CANCELLED),
                draft("johto", DraftStatus.COMPLETED),
                draft("kanto", DraftStatus.COMPLETED),
                draft("hoenn", DraftStatus.PENDING)));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(ClosedListEntity.class)))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null));

        new ClearStaleTiersMigration(mongoTemplate).migrate();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(ClosedListEntity.class));
        Document filter = query.getValue().getQueryObject();
        assertThat(filter.get("tier", Document.class)).containsEntry("$exists", true);
        // Las ligas sin draft (no están en la lista) también se limpian: solo se salvan johto y hoenn
        assertThat((Collection<Object>) filter.get("leagueId", Document.class).get("$nin"))
                .containsExactlyInAnyOrder("johto", "hoenn");
        assertThat(update.getValue().getUpdateObject().get("$unset", Document.class)).containsKey("tier");
    }

    @Test
    void migrate_withoutDrafts_clearsEveryTier() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(DraftEntity.class))).thenReturn(List.of());
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(ClosedListEntity.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        new ClearStaleTiersMigration(mongoTemplate).migrate();

        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(ClosedListEntity.class));
    }
}
