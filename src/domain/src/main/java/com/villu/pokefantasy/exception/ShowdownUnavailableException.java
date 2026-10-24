package com.villu.pokefantasy.exception;

/** Pokémon Showdown no responde (caído o fuera de tiempo). Se responde con 502. */
public class ShowdownUnavailableException extends RuntimeException {
    public ShowdownUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
