package com.villu.pokefantasy.migration;

import com.mongodb.client.result.UpdateResult;
import com.villu.pokefantasy.repository.entity.UserEntity;
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

class DropUserPokemonsMigrationTest {

    @Test
    void migrate_unsetsLegacyFieldOnlyWherePresent() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(UserEntity.class)))
                .thenReturn(UpdateResult.acknowledged(2, 2L, null));

        new DropUserPokemonsMigration(mongoTemplate).migrate();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject())
                .isEqualTo(new Document("pokemons", new Document("$exists", true)));
        assertThat(update.getValue().getUpdateObject())
                .isEqualTo(new Document("$unset", new Document("pokemons", 1)));
    }

    @Test
    void migrate_nothingToRemove_doesNotFail() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(UserEntity.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        new DropUserPokemonsMigration(mongoTemplate).migrate();

        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(UserEntity.class));
    }
}
