package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

import java.util.List;

public record StartDraftCommand(List<String> turnOrder) implements Command {}
