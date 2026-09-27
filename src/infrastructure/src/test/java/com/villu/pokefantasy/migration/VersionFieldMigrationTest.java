package com.villu.pokefantasy.migration;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionFieldMigrationTest {

    @Test
    void migrate_setsInitialVersionOnlyWhereMissing_forEveryVersionedEntity() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null));

        new VersionFieldMigration(mongoTemplate).migrate();

        for (Class<?> entity : VersionFieldMigration.VERSIONED_ENTITIES) {
            ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
            verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(entity));

            assertThat(query.getValue().getQueryObject())
                    .isEqualTo(new Document("version", new Document("$exists", false)));
            assertThat(update.getValue().getUpdateObject())
                    .isEqualTo(new Document("$set", new Document("version", 0L)));
        }
    }
}
