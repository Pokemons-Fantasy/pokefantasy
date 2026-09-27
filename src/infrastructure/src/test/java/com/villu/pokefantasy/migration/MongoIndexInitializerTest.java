package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoIndexInitializerTest {

    @Test
    void createsDeclaredIndexes_andAFailingOneDoesNotStopTheRest() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations users = mock(IndexOperations.class);
        IndexOperations others = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(others);
        when(mongoTemplate.indexOps(UserEntity.class)).thenReturn(users);
        when(users.createIndex(any(IndexDefinition.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key: name 'ash'"));

        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(new MongoCustomConversions(java.util.List.of()).getSimpleTypeHolder());
        MongoIndexInitializer initializer = new MongoIndexInitializer(mongoTemplate, mappingContext);

        assertThatCode(initializer::createIndexes).doesNotThrowAnyException();
        verify(users, atLeastOnce()).createIndex(any(IndexDefinition.class));
        verify(others, atLeastOnce()).createIndex(any(IndexDefinition.class));
    }

    @Test
    void entityThatCannotBeResolved_isSkipped() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(mock(IndexOperations.class));

        // Contexto sin tipos simples de Mongo: algunas entidades no se pueden resolver.
        assertThatCode(new MongoIndexInitializer(mongoTemplate, new MongoMappingContext())::createIndexes)
                .doesNotThrowAnyException();
    }
}
