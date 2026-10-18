package com.villu.pokefantasy.migration;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        assertThatCode(initializer(mongoTemplate)::createIndexes).doesNotThrowAnyException();
        verify(users, atLeastOnce()).createIndex(any(IndexDefinition.class));
        verify(others, atLeastOnce()).createIndex(any(IndexDefinition.class));
        // Un error que no es de opciones no toca el índice que ya hay.
        verify(users, never()).dropIndex(anyString());
    }

    @Test
    void entityThatCannotBeResolved_isSkipped() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(mock(IndexOperations.class));

        // Contexto sin tipos simples de Mongo: algunas entidades no se pueden resolver.
        assertThatCode(new MongoIndexInitializer(mongoTemplate, new MongoMappingContext())::createIndexes)
                .doesNotThrowAnyException();
    }

    @Test
    void optionsConflict_dropsTheOldIndexAndRecreatesIt() {
        MongoTemplate mongoTemplate = mongoTemplate();
        IndexOperations users = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(UserEntity.class)).thenReturn(users);
        when(users.createIndex(argThat(MongoIndexInitializerTest::isNameLower)))
                .thenThrow(optionsConflict())
                .thenReturn("nameLower");
        when(users.getIndexInfo()).thenReturn(List.of(nonUniqueNameLower()));

        assertThatCode(initializer(mongoTemplate)::createIndexes).doesNotThrowAnyException();

        verify(users).dropIndex("nameLower");
        assertThat(nameLowerCreations(users)).hasSize(2)
                .allSatisfy(index -> assertThat(index.getIndexOptions().getBoolean("unique")).isTrue());
    }

    @Test
    void conflictThenDuplicates_fallsBackToNonUnique() {
        MongoTemplate mongoTemplate = mongoTemplate();
        IndexOperations users = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(UserEntity.class)).thenReturn(users);
        when(users.createIndex(argThat(MongoIndexInitializerTest::isNameLower)))
                .thenThrow(optionsConflict())
                .thenThrow(new DuplicateKeyException("E11000 duplicate key: nameLower 'ash'"))
                .thenReturn("nameLower");
        when(users.getIndexInfo()).thenReturn(List.of(nonUniqueNameLower()));

        assertThatCode(initializer(mongoTemplate)::createIndexes).doesNotThrowAnyException();

        List<IndexDefinition> created = nameLowerCreations(users);
        assertThat(created).hasSize(3);
        IndexDefinition fallback = created.get(2);
        assertThat(fallback.getIndexKeys()).isEqualTo(created.get(0).getIndexKeys());
        assertThat(fallback.getIndexOptions()).doesNotContainKey("unique").containsEntry("name", "nameLower");
    }

    @Test
    void conflictAndEverythingElseFails_doesNotStopStartup() {
        MongoTemplate mongoTemplate = mongoTemplate();
        IndexOperations users = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(UserEntity.class)).thenReturn(users);
        when(users.createIndex(argThat(MongoIndexInitializerTest::isNameLower))).thenThrow(optionsConflict());
        when(users.getIndexInfo()).thenThrow(new IllegalStateException("not authorized"));

        assertThatCode(initializer(mongoTemplate)::createIndexes).doesNotThrowAnyException();
        verify(users, never()).dropIndex(anyString());
    }

    private static MongoTemplate mongoTemplate() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(mock(IndexOperations.class));
        return mongoTemplate;
    }

    private static MongoIndexInitializer initializer(MongoTemplate mongoTemplate) {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        return new MongoIndexInitializer(mongoTemplate, mappingContext);
    }

    private static boolean isNameLower(IndexDefinition index) {
        return index != null && index.getIndexKeys().containsKey("nameLower");
    }

    private static List<IndexDefinition> nameLowerCreations(IndexOperations users) {
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(users, atLeastOnce()).createIndex(captor.capture());
        return captor.getAllValues().stream().filter(MongoIndexInitializerTest::isNameLower).toList();
    }

    /** Lo que devuelve Mongo al crear un índice que ya existe con otras opciones (IndexKeySpecsConflict). */
    private static UncategorizedMongoDbException optionsConflict() {
        BsonDocument response = new BsonDocument("ok", new BsonInt32(0))
                .append("code", new BsonInt32(86))
                .append("errmsg", new BsonString("An existing index has the same name as the requested index"));
        return new UncategorizedMongoDbException("Index conflict",
                new MongoCommandException(response, new ServerAddress()));
    }

    private static IndexInfo nonUniqueNameLower() {
        return new IndexInfo(List.of(IndexField.create("nameLower", Sort.Direction.ASC)), "nameLower", false, false, "");
    }
}
