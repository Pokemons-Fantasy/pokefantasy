package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

public record DraftPickCommand(String username, String pokemonName) implements Command {}
