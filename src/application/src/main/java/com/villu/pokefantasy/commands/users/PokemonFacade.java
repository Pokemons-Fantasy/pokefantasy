package com.villu.pokefantasy.commands.users;


import com.villu.pokefantasy.commands.pokemons.add.AddPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandResponse;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
        // Aquí podrías implementar la lógica para agregar pokemons, por ejemplo:
        // - Validar los nombres de los pokemons
        // - Llamar a un comando para agregar cada pokemon a la base de datos o cache
        // - Manejar posibles errores o excepciones
        mediator.send(new AddPokemonCommand(pokemonsName));
    }
}
