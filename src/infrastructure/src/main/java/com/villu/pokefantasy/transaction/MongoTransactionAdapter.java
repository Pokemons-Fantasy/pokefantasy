package com.villu.pokefantasy.transaction;

import com.mongodb.MongoException;
import com.villu.pokefantasy.exception.StaleOperationException;
import com.villu.pokefantasy.ports.TransactionPort;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Ejecuta cada comando dentro de una transacción multi-documento de MongoDB.
 *
 * <p>Si otro comando modifica a la vez los mismos documentos (conflicto de escritura o
 * {@code @Version} desactualizado), la transacción se aborta y se reintenta desde cero,
 * releyendo los datos. Tras {@link #MAX_ATTEMPTS} intentos se devuelve un 409.
 *
 * <p>Las transacciones requieren un replica set (Atlas lo es siempre). Contra un MongoDB
 * standalone el trabajo se ejecuta sin transacción y sin reintentos, avisando en el log.
 */
@Component
@Slf4j
public class MongoTransactionAdapter implements TransactionPort {

    static final int MAX_ATTEMPTS = 3;
    static final String CONFLICT_MESSAGE =
            "Otro jugador modificó los datos al mismo tiempo. Inténtalo de nuevo.";

    private static final String TRANSIENT_TRANSACTION_ERROR = "TransientTransactionError";

    private final Supplier<Boolean> transactionSupportProbe;
    private final PlatformTransactionManager transactionManager;
    private volatile Boolean transactionsSupported;
    private volatile TransactionTemplate transactionTemplate;

    @Autowired
    public MongoTransactionAdapter(MongoTemplate mongoTemplate) {
        this(new MongoTransactionManager(mongoTemplate.getMongoDatabaseFactory()),
                () -> isReplicaSetOrSharded(mongoTemplate));
    }

    MongoTransactionAdapter(PlatformTransactionManager transactionManager, Supplier<Boolean> transactionSupportProbe) {
        this.transactionManager = transactionManager;
        this.transactionSupportProbe = transactionSupportProbe;
    }

    @Override
    public <T> T execute(TransactionalWork<T> work) throws Exception {
        // Comando anidado (p. ej. auto-pick delegando en draft-pick): se une a la transacción en curso.
        if (TransactionSynchronizationManager.isActualTransactionActive() || !transactionsSupported()) {
            return work.run();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return runInTransaction(work);
            } catch (Exception exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Transaction conflict persisted after {} attempts", attempt);
                    throw new IllegalStateException(CONFLICT_MESSAGE, exception);
                }
                log.debug("Transaction conflict on attempt {}, retrying", attempt, exception);
            }
        }
    }

    private <T> T runInTransaction(TransactionalWork<T> work) throws Exception {
        StaleOperationException[] committedFailure = new StaleOperationException[1];
        T result;
        try {
            result = transactionTemplate.execute(status -> {
                try {
                    return work.run();
                } catch (StaleOperationException stale) {
                    // Rechazo cuyas escrituras de limpieza deben persistirse: se confirma y luego se lanza.
                    committedFailure[0] = stale;
                    return null;
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new CheckedExceptionHolder(exception);
                }
            });
        } catch (CheckedExceptionHolder holder) {
            throw holder.checked;
        }
        if (committedFailure[0] != null) {
            throw committedFailure[0];
        }
        return result;
    }

    private boolean transactionsSupported() {
        Boolean supported = transactionsSupported;
        if (supported != null) {
            return supported;
        }
        synchronized (this) {
            if (transactionsSupported == null) {
                try {
                    boolean probe = Boolean.TRUE.equals(transactionSupportProbe.get());
                    if (probe) {
                        transactionTemplate = new TransactionTemplate(transactionManager);
                        log.info("MongoDB supports transactions — commands run atomically");
                    } else {
                        log.warn("MongoDB is a standalone server — commands run WITHOUT transactions. "
                                + "Use a replica set (e.g. Atlas) to get atomic, conflict-safe writes.");
                    }
                    transactionsSupported = probe;
                } catch (RuntimeException exception) {
                    // MongoDB inaccesible: no se cachea, se vuelve a comprobar en el siguiente comando.
                    log.warn("Could not determine MongoDB transaction support: {}", exception.getMessage());
                    return false;
                }
            }
            return transactionsSupported;
        }
    }

    static boolean isReplicaSetOrSharded(MongoTemplate mongoTemplate) {
        Document hello = mongoTemplate.executeCommand(new Document("hello", 1));
        return hello.containsKey("setName") || "isdbgrid".equals(hello.get("msg"));
    }

    static boolean isRetryable(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof OptimisticLockingFailureException) {
                return true;
            }
            if (current instanceof MongoException mongoException
                    && mongoException.hasErrorLabel(TRANSIENT_TRANSACTION_ERROR)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static final class CheckedExceptionHolder extends RuntimeException {
        private final Exception checked;

        private CheckedExceptionHolder(Exception checked) {
            super(checked);
            this.checked = checked;
        }
    }
}
