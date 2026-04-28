package com.villu.pokefantasy.commands.pokemons.saved;

import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandResponse;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.repository.PokemonRepository;
import com.villu.pokefantasy.repository.entity.PokemonEntity;
import com.villu.pokefantasy.response.PokemonsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GetSavedPokemonsCommandHandler implements CommandHandler<GetSavedPokemonsCommand, List<PokemonsResponse>> {

    private final PokemonMapper pokemonMapper;
    private final PokemonRepository pokemonRepository;
    private final PokemonApiPort pokemonApiPort;

    public GetSavedPokemonsCommandHandler(PokemonMapper pokemonMapper, PokemonRepository pokemonRepository, PokemonApiPort pokemonApiPort) {
        this.pokemonMapper = pokemonMapper;
        this.pokemonRepository = pokemonRepository;
        this.pokemonApiPort = pokemonApiPort;
    }

    @Override
    public List<PokemonsResponse> handle(GetSavedPokemonsCommand command) throws Exception {
        List<PokemonEntity> savedEntities = pokemonRepository.getAllPokemons();
        List<PokemonsResponse> responseList = new ArrayList<>();

        for (PokemonEntity entity : savedEntities) {
            try {
                GetPokemonCommandResponse pokemonData = pokemonMapper.dtoToResponse(
                        pokemonApiPort.fetchPokemonById(entity.getUrl(), entity.getName())
                );
                responseList.add(pokemonMapper.commandToResponse(pokemonData));
            } catch (Exception e) {
                log.error("Error fetching data for pokemon {}: {}", entity.getName(), e.getMessage());
            }
        }

        return responseList;
    }

    @Override
    public Class<GetSavedPokemonsCommand> commandType() {
        return GetSavedPokemonsCommand.class;
    }
}
