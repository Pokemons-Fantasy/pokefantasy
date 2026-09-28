package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La caché de Redis sobrevive a los despliegues: el formato nuevo tiene que leer el antiguo. */
class PokemonCacheDtoJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, PokemonCacheDto.class);

    @Test
    void oldCacheWithoutTypes_readsWithNullTypes() {
        List<PokemonCacheDto> cached = mapper.readValue("[{\"url\":\"u\",\"name\":\"pikachu\",\"id\":25}]", listType);

        assertThat(cached).containsExactly(new PokemonCacheDto("u", "pikachu", 25, null));
    }

    @Test
    void newCache_roundTripsTheTypes() {
        List<PokemonCacheDto> value = List.of(new PokemonCacheDto("u", "bulbasaur", 1, List.of("grass", "poison")));

        List<PokemonCacheDto> read = mapper.readValue(mapper.writeValueAsString(value), listType);

        assertThat(read).isEqualTo(value);
    }
}
