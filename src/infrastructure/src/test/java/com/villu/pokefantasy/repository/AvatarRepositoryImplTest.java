package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.AvatarEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarRepositoryImplTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final AvatarRepositoryImpl repository = new AvatarRepositoryImpl(mongoTemplate);

    @Test
    void save_upsertsById() {
        AvatarEntity avatar = new AvatarEntity("ash", new byte[]{1}, "image/jpeg", Instant.EPOCH);

        repository.save(avatar);

        verify(mongoTemplate).save(avatar);
    }

    @Test
    void findByUsername_found() {
        AvatarEntity avatar = new AvatarEntity("ash", new byte[]{1}, "image/jpeg", Instant.EPOCH);
        when(mongoTemplate.findById("ash", AvatarEntity.class)).thenReturn(avatar);

        assertThat(repository.findByUsername("ash")).containsSame(avatar);
    }

    @Test
    void findByUsername_missing_isEmpty() {
        assertThat(repository.findByUsername("misty")).isEmpty();
    }

    @Test
    void deleteByUsername_removesById() {
        repository.deleteByUsername("ash");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(query.capture(), eq(AvatarEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("_id", "ash"));
    }
}
