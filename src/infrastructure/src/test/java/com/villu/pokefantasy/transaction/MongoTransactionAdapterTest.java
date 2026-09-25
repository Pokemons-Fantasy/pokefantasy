package com.villu.pokefantasy.transaction;

import com.mongodb.MongoException;
import com.villu.pokefantasy.exception.StaleOperationException;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MongoTransactionAdapterTest {

    private PlatformTransactionManager transactionManager;
    private AtomicInteger probes;
    private MongoTransactionAdapter adapter;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        probes = new AtomicInteger();
        adapter = new MongoTransactionAdapter(transactionManager, () -> {
            probes.incrementAndGet();
            return true;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ── Sin soporte de transacciones ─────────────────────────────────────────

    @Test
    void execute_standaloneServer_runsWorkWithoutTransaction() throws Exception {
        MongoTransactionAdapter standalone = new MongoTransactionAdapter(transactionManager, () -> false);

        assertThat(standalone.execute(() -> "done")).isEqualTo("done");
        assertThat(standalone.execute(() -> "again")).isEqualTo("again");

        verifyNoInteractions(transactionManager);
    }

    @Test
    void execute_standaloneServer_doesNotRetryConflicts() {
        MongoTransactionAdapter standalone = new MongoTransactionAdapter(transactionManager, () -> false);
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> standalone.execute(() -> {
            runs.incrementAndGet();
            throw new OptimisticLockingFailureException("stale");
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(runs).hasValue(1);
    }

    @Test
    void execute_probeFails_runsWithoutTransactionAndProbesAgainLater() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        MongoTransactionAdapter flaky = new MongoTransactionAdapter(transactionManager, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("mongo down");
            }
            return true;
        });

        assertThat(flaky.execute(() -> "first")).isEqualTo("first");
        verifyNoInteractions(transactionManager);

        assertThat(flaky.execute(() -> "second")).isEqualTo("second");
        verify(transactionManager).commit(any());
        assertThat(attempts).hasValue(2);
    }

    // ── Con transacciones ────────────────────────────────────────────────────

    @Test
    void execute_success_commitsAndProbesOnlyOnce() throws Exception {
        assertThat(adapter.execute(() -> "ok")).isEqualTo("ok");
        assertThat(adapter.execute(() -> "ok2")).isEqualTo("ok2");

        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager, never()).rollback(any());
        assertThat(probes).hasValue(1);
    }

    @Test
    void execute_runtimeException_rollsBackAndRethrowsSameException() {
        IllegalArgumentException failure = new IllegalArgumentException("invalid");

        assertThatThrownBy(() -> adapter.execute(() -> {
            throw failure;
        })).isSameAs(failure);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void execute_checkedException_rollsBackAndRethrowsSameException() {
        IOException failure = new IOException("checked");

        assertThatThrownBy(() -> adapter.execute(() -> {
            throw failure;
        })).isSameAs(failure);

        verify(transactionManager).rollback(any());
    }

    @Test
    void execute_optimisticLockConflict_retriesUntilSuccess() throws Exception {
        AtomicInteger runs = new AtomicInteger();

        String result = adapter.execute(() -> {
            if (runs.incrementAndGet() < MongoTransactionAdapter.MAX_ATTEMPTS) {
                throw new OptimisticLockingFailureException("stale");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(runs).hasValue(MongoTransactionAdapter.MAX_ATTEMPTS);
        verify(transactionManager, times(MongoTransactionAdapter.MAX_ATTEMPTS - 1)).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test
    void execute_transientTransactionError_isRetried() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        MongoException writeConflict = new MongoException(112, "WriteConflict");
        writeConflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);

        String result = adapter.execute(() -> {
            if (runs.incrementAndGet() == 1) {
                throw new UncategorizedMongoDbException("write conflict", writeConflict);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(runs).hasValue(2);
    }

    @Test
    void execute_persistentConflict_givesUpWithConflictMessage() {
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> adapter.execute(() -> {
            runs.incrementAndGet();
            throw new OptimisticLockingFailureException("stale");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MongoTransactionAdapter.CONFLICT_MESSAGE)
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);

        assertThat(runs).hasValue(MongoTransactionAdapter.MAX_ATTEMPTS);
    }

    @Test
    void execute_nonTransientMongoError_isNotRetried() {
        AtomicInteger runs = new AtomicInteger();
        MongoException mongoError = new MongoException(2, "BadValue");

        assertThatThrownBy(() -> adapter.execute(() -> {
            runs.incrementAndGet();
            throw new UncategorizedMongoDbException("bad value", mongoError);
        })).isInstanceOf(UncategorizedMongoDbException.class);

        assertThat(runs).hasValue(1);
    }

    @Test
    void execute_staleOperation_commitsThenThrows() {
        StaleOperationException stale = new StaleOperationException("ya no es válida");

        assertThatThrownBy(() -> adapter.execute(() -> {
            throw stale;
        })).isSameAs(stale);

        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void execute_nestedInsideActiveTransaction_joinsWithoutNewTransaction() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThat(adapter.execute(() -> "inner")).isEqualTo("inner");

        verifyNoInteractions(transactionManager);
    }

    // ── Detección de replica set ─────────────────────────────────────────────

    @Test
    void isReplicaSetOrSharded_detectsTopology() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        when(mongoTemplate.executeCommand(any(Document.class))).thenReturn(new Document("setName", "rs0"));
        assertThat(MongoTransactionAdapter.isReplicaSetOrSharded(mongoTemplate)).isTrue();

        when(mongoTemplate.executeCommand(any(Document.class))).thenReturn(new Document("msg", "isdbgrid"));
        assertThat(MongoTransactionAdapter.isReplicaSetOrSharded(mongoTemplate)).isTrue();

        when(mongoTemplate.executeCommand(any(Document.class))).thenReturn(new Document("isWritablePrimary", true));
        assertThat(MongoTransactionAdapter.isReplicaSetOrSharded(mongoTemplate)).isFalse();
    }

    @Test
    void springConstructor_probesServerLazily() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.getMongoDatabaseFactory()).thenReturn(mock(MongoDatabaseFactory.class));
        when(mongoTemplate.executeCommand(any(Document.class))).thenReturn(new Document("isWritablePrimary", true));

        MongoTransactionAdapter springAdapter = new MongoTransactionAdapter(mongoTemplate);
        verify(mongoTemplate, never()).executeCommand(any(Document.class));

        assertThat(springAdapter.execute(() -> "standalone")).isEqualTo("standalone");
        verify(mongoTemplate).executeCommand(any(Document.class));
    }
}
