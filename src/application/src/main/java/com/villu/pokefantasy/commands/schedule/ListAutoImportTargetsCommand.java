package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/** Consulta: partidos que el import automático debe mirar (jornada activa, mercado cerrado, los dos vinculados). */
public record ListAutoImportTargetsCommand() implements Command {}
