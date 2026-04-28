package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.Pokemons;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "users")
public class UserEntity{
    private String id;
    private String name;
    private String password;
    private List<Pokemons> pokemons;
}
