package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRepositoryImplAvatarTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final UserRepositoryImpl repository = new UserRepositoryImpl(mongoTemplate);

    @Test
    void findByUsernames_queriesByNameAndProjectsAvatarVersion() {
        UserEntity ash = new UserEntity();
        when(mongoTemplate.find(any(Query.class), eq(UserEntity.class))).thenReturn(List.of(ash));

        List<UserEntity> result = repository.findByUsernames(List.of("ash", "brock"));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject())
                .isEqualTo(new Document("name", new Document("$in", List.of("ash", "brock"))));
        assertThat(query.getValue().getFieldsObject())
                .isEqualTo(new Document("name", 1).append("avatarVersion", 1));
        assertThat(result).containsExactly(ash);
    }

    @Test
    void findByUsernames_empty_skipsQuery() {
        assertThat(repository.findByUsernames(List.of())).isEmpty();
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void setAvatarVersion_setsFieldAndBumpsVersion() {
        repository.setAvatarVersion("ash", 42L);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("name", "ash"));
        assertThat(update.getValue().getUpdateObject()).isEqualTo(new Document()
                .append("$set", new Document("avatarVersion", 42L))
                .append("$inc", new Document("version", 1)));
    }
}
