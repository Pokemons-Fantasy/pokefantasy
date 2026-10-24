package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryImplShowdownTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final UserRepositoryImpl repository = new UserRepositoryImpl(mongoTemplate);

    @Test
    void setShowdownAccount_setsBothFieldsAndIncrementsVersion() {
        repository.setShowdownAccount("ash", "Ash Ketchum", "ashketchum");

        Document update = capturedUpdate();
        assertThat(update.get("$set", Document.class))
                .containsEntry("showdownName", "Ash Ketchum")
                .containsEntry("showdownId", "ashketchum");
        assertThat(update.get("$inc", Document.class)).containsEntry("version", 1);
    }

    @Test
    void clearShowdownAccount_unsetsBothFields() {
        repository.clearShowdownAccount("ash");

        Document update = capturedUpdate();
        assertThat(update.get("$unset", Document.class)).containsKeys("showdownName", "showdownId");
        assertThat(update).doesNotContainKey("$set");
    }

    @Test
    void findByShowdownId_queriesByShowdownId() {
        UserEntity misty = new UserEntity();
        misty.setName("misty");
        when(mongoTemplate.findOne(any(Query.class), eq(UserEntity.class))).thenReturn(misty);

        assertThat(repository.findByShowdownId("ashketchum")).contains(misty);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("showdownId", "ashketchum"));
    }

    private Document capturedUpdate() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("name", "ash"));
        return update.getValue().getUpdateObject();
    }
}
