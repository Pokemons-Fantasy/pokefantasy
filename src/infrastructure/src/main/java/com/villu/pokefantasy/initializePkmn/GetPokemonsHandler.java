package com.villu.pokefantasy.initializePkmn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class GetPokemonsHandler {

    private static final String POKE_API_URL = "https://pokeapi.co/api/v2/pokemon?limit=100000&offset=0";

    public GetPokemonsResponse handle() {
        // Implement the logic to retrieve pokemons based on the request parameters
        // This is a placeholder implementation and should be replaced with actual logic
        //Aqui se obtendran todos los pokemons cuando arranque el programa, se guardaran en memoria
        RestTemplate restTemplate = new RestTemplate();
        log.info("Get Pokemons API URL: {}", POKE_API_URL);
        GetPokemonsResponse response = restTemplate.getForObject(POKE_API_URL, GetPokemonsResponse.class);
        return response;
    }
}
