package com.villu.pokefantasy.mediator;

/**
 * Maneja un Command concreto.
 */
public interface CommandHandler<C extends Command, R> {

    R handle(C command) throws Exception;

    Class<C> commandType();
}

