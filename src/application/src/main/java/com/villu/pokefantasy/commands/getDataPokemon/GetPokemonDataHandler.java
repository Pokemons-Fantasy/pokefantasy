package com.villu.pokefantasy.commands.getDataPokemon;

import com.villu.pokefantasy.dto.Pokemons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class GetPokemonDataHandler {

    public Pokemons handle(GetPokemonDataCommand command) throws Exception {
        log.info("Searching data for pokemon: {}", command.getName());
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.getForObject(command.getUrl(), Pokemons.class);
        } catch (Exception e) {
            log.error("Error fetching data for pokemon: {}", command.getName(), e);
            throw new Exception("Failed to fetch data for pokemon: " + command.getName(), e);
        }
    }

}
