package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftRepositoryImplTest {

    @Test
    void findAllInProgress_queriesInProgressDraftsOnly() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        DraftEntity draft = new DraftEntity();
        when(mongoTemplate.find(any(Query.class), eq(DraftEntity.class))).thenReturn(List.of(draft));

        List<DraftEntity> result = new DraftRepositoryImpl(mongoTemplate).findAllInProgress();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(DraftEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("status", DraftStatus.IN_PROGRESS));
        assertThat(result).containsExactly(draft);
    }
}
