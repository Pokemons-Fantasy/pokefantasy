package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

/** Ligas con un draft en curso cuyo turno actual ya ha vencido. Solo para el job del servidor. */
public record ListExpiredDraftTurnsCommand() implements Command {}
