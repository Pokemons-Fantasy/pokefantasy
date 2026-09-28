package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityEventMongoRepositoryTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ActivityEventMongoRepository repository = new ActivityEventMongoRepository(mongoTemplate);

    private Query capturedFind() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(ActivityEventEntity.class), eq("activity_events"));
        return query.getValue();
    }

    private Query capturedCount() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(ActivityEventEntity.class), eq("activity_events"));
        return query.getValue();
    }

    /** Valores del {@code $in} de tipos (Criteria guarda la colección tal cual, aquí un Set). */
    @SuppressWarnings("unchecked")
    private static Collection<Object> typesIn(Document query) {
        return (Collection<Object>) ((Document) query.get("type")).get("$in");
    }

    @Test
    void find_wholeLeague_newestFirstWithPaging() {
        ActivityEventEntity event = new ActivityEventEntity();
        when(mongoTemplate.find(any(Query.class), eq(ActivityEventEntity.class), eq("activity_events")))
                .thenReturn(List.of(event));

        List<ActivityEventEntity> result = repository.find(new ActivityEventFilter("l1", null, null), 2, 20);

        Query query = capturedFind();
        assertThat(query.getQueryObject()).isEqualTo(new Document("leagueId", "l1"));
        assertThat(query.getSortObject()).isEqualTo(new Document("createdAt", -1));
        assertThat(query.getSkip()).isEqualTo(40);
        assertThat(query.getLimit()).isEqualTo(20);
        assertThat(result).containsExactly(event);
    }

    @Test
    void find_byUserAndTypes_combinesBothFilters() {
        repository.find(new ActivityEventFilter("l1", "ash", Set.of(ActivityEventType.STEAL)), 0, 20);

        Document q = capturedFind().getQueryObject();
        assertThat(q.get("leagueId")).isEqualTo("l1");
        assertThat(typesIn(q)).containsExactly(ActivityEventType.STEAL);
        assertThat(q.get("$or")).isEqualTo(List.of(new Document("actorUsername", "ash"), new Document("targetUsername", "ash")));
    }

    @Test
    void count_usesTheSameCriteriaWithoutPaging() {
        when(mongoTemplate.count(any(Query.class), eq(ActivityEventEntity.class), eq("activity_events"))).thenReturn(7L);

        long count = repository.count(new ActivityEventFilter("l1", null, Set.of(ActivityEventType.MATCH_RESULT)));

        Query query = capturedCount();
        assertThat(query.getQueryObject()).containsOnlyKeys("leagueId", "type");
        assertThat(typesIn(query.getQueryObject())).containsExactly(ActivityEventType.MATCH_RESULT);
        assertThat(query.getLimit()).isZero();
        assertThat(count).isEqualTo(7L);
    }
}
