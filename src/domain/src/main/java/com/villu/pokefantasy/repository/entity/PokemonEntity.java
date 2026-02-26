package com.villu.pokefantasy.repository.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "pokemonsChoosen")
public class PokemonEntity {

    private String id;
    private String name;
    private String url;
}
