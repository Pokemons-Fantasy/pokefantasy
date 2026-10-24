package com.villu.pokefantasy.commands.users.showdown;

import com.villu.pokefantasy.mediator.Command;

/** {@code showdownName} y {@code showdownId} ya comprobados contra Showdown (los resuelve la facade). */
public record LinkShowdownCommand(String username, String showdownName, String showdownId) implements Command {}
