package com.villu.pokefantasy.commands.users.add.pokemon.user;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mediator.Command;

import java.util.List;

public record AddPokemonUserCommand(List<Pokemons> pokemons, String nameUser) implements Command {
}
