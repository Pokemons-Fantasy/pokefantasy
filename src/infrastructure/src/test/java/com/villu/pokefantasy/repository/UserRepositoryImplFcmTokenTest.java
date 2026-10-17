package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserRepositoryImplFcmTokenTest {

    @Test
    void addFcmToken_pushesAndKeepsOnlyTheNewest() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        new UserRepositoryImpl(mongoTemplate).addFcmToken("ash", "tok", 3);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("name", "ash"));
        Document updateObject = update.getValue().getUpdateObject();
        Update.Modifiers push = (Update.Modifiers) updateObject.get("$push", Document.class).get("fcmTokens");
        Map<String, Object> modifiers = push.getModifiers().stream()
                .collect(Collectors.toMap(Update.Modifier::getKey, Update.Modifier::getValue));
        assertThat(modifiers.get("$each")).asInstanceOf(InstanceOfAssertFactories.ARRAY).containsExactly("tok");
        assertThat(modifiers.get("$slice")).isEqualTo(-3);
        assertThat(updateObject.get("$inc", Document.class)).containsEntry("version", 1);
    }
}
