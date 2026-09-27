package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/** Envía los avisos de cierre de ventana pendientes de una liga; devuelve si envió alguno. */
public record SendWindowRemindersCommand(String leagueId) implements Command {}
