package com.villu.pokefantasy.commands.users;


import com.villu.pokefantasy.commands.pokemons.add.AddPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.saved.GetSavedPokemonsCommand;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PokemonFacade {

    private final Mediator mediator;

    private final PokemonMapper pokemonMapper;

    public PokemonFacade(Mediator mediator, PokemonMapper pokemonMapper) {
        this.mediator = mediator;
        this.pokemonMapper = pokemonMapper;
    }

    public PokemonsResponse getPokemon(int id) throws Exception {
        return pokemonMapper.commandToResponse(mediator.send(new GetPokemonCommand(id)));
    }

    public void addPokemons(List<String> pokemonsName) throws Exception {
        mediator.send(new AddPokemonCommand(pokemonsName));
    }

    public List<PokemonsResponse> getSavedPokemons() throws Exception {
        return mediator.send(new GetSavedPokemonsCommand());
    }
}
