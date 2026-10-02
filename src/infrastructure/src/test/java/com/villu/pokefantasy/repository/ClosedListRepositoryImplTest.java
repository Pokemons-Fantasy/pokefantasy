package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClosedListRepositoryImplTest {

    @Test
    void clearTiers_unsetsTierOnTheWholeLeaguePool() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        new ClosedListRepositoryImpl(mongoTemplate).clearTiers("l1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(ClosedListEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
        Document updateObject = update.getValue().getUpdateObject();
        assertThat(updateObject.get("$unset", Document.class)).containsKey("tier");
        assertThat(updateObject.get("$inc", Document.class)).containsEntry("version", 1);
    }
}
