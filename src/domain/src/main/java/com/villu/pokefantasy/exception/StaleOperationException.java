package com.villu.pokefantasy.exception;

/**
 * Operación rechazada porque los datos en los que se basaba ya no son válidos, pero cuyas
 * escrituras de limpieza previas (p. ej. cancelar un trade obsoleto) sí deben persistirse.
 * La transacción del comando se confirma antes de propagar la excepción (HTTP 409).
 */
public class StaleOperationException extends IllegalStateException {
    public StaleOperationException(String message) {
        super(message);
    }
}
