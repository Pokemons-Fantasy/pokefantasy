package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

/** Autoriza suscribirse a los eventos en tiempo real del draft de una liga (solo miembros). */
public record WatchDraftCommand(String leagueId, String requestingUsername) implements Command {}
