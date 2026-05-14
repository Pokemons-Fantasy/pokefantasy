package com.villu.pokefantasy.commands.pokemons.available;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.response.AvailablePokemonResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetAvailablePokemonsCommandHandler implements CommandHandler<GetAvailablePokemonsCommand, List<AvailablePokemonResponse>> {

    private static final String SPRITE_URL_TEMPLATE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/%d.png";

    private final CachePort cachePort;

    public GetAvailablePokemonsCommandHandler(CachePort cachePort) {
        this.cachePort = cachePort;
    }

    @Override
    public List<AvailablePokemonResponse> handle(GetAvailablePokemonsCommand command) {
        return cachePort.getPokemon("pokemons").stream()
                .map(p -> new AvailablePokemonResponse(
                        p.getId(),
                        p.getName(),
                        String.format(SPRITE_URL_TEMPLATE, p.getId())
                ))
                .toList();
    }

    @Override
    public Class<GetAvailablePokemonsCommand> commandType() {
        return GetAvailablePokemonsCommand.class;
    }
}
