package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.response.PokemonResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class PokemonApiAdapter implements PokemonApiPort {

    private static final String POKE_API_URL = "https://pokeapi.co/api/v2/pokemon?limit=100000&offset=0";

    @Override
    public PokemonResponseApi fetchAllPokemons() {
        RestTemplate restTemplate = new RestTemplate();
        log.info("Get Pokemons API URL: {}", POKE_API_URL);
        return restTemplate.getForObject(POKE_API_URL, PokemonResponseApi.class);
    }

    @Override
    public Pokemons fetchPokemonById(String url,String name) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.getForObject(url, Pokemons.class);
        } catch (Exception e) {
            log.error("Error fetching data for pokemon: {}", name, e);
            throw new Exception("Failed to fetch data for pokemon: " + name, e);
        }
    }

    @Override
    public Pokemons fetchPokemonData(String url) {
        return null;
    }


}
