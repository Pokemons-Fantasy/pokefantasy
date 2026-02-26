package com.villu.pokefantasy.mediator;

public interface Mediator {

    <R, C extends Command> R send(C command) throws Exception;
}

