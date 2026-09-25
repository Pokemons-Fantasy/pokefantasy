package com.villu.pokefantasy.ports;

/**
 * Ejecuta una unidad de trabajo de forma atómica: o se persisten todas sus escrituras o ninguna.
 * La implementación puede reintentar la unidad completa ante conflictos de concurrencia,
 * así que el trabajo debe releer de la base de datos todo lo que valida.
 */
public interface TransactionPort {

    <T> T execute(TransactionalWork<T> work) throws Exception;

    @FunctionalInterface
    interface TransactionalWork<T> {
        T run() throws Exception;
    }
}
