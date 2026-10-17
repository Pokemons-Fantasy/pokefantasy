package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeagueRepositoryImplTest {

    @Test
    void countByCreatedBy_countsByCreator() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.count(any(Query.class), eq(LeagueEntity.class))).thenReturn(4L);

        long count = new LeagueRepositoryImpl(mongoTemplate).countByCreatedBy("ash");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(LeagueEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("createdBy", "ash"));
        assertThat(count).isEqualTo(4L);
    }
}
