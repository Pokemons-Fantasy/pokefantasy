package com.villu.pokefantasy.migration;

import com.mongodb.client.result.UpdateResult;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserNameLowerMigrationTest {

    @Test
    void migrate_fillsNameLowerOnlyWhereMissing() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(UserEntity.class)))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        UserNameLowerMigration migration = new UserNameLowerMigration(mongoTemplate);
        migration.migrate();
        migration.migrate(); // segunda vez: nada que hacer

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).updateMulti(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("nameLower", new Document("$exists", false))
                .append("name", new Document("$exists", true)));
        assertThat(update.getValue()).isInstanceOf(AggregationUpdate.class);
    }
}
