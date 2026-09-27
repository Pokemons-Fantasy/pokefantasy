package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/** Ligas con algún aviso de cierre de ventana pendiente (solo lectura; lo usa el job). */
public record ListDueWindowRemindersCommand() implements Command {}
