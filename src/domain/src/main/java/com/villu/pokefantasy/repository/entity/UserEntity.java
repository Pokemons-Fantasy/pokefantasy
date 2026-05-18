package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Role;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "users")
public class UserEntity {
    private String id;
    @Indexed(unique = true)
    private String name;
    private String password;
    private Role role;
    private List<Pokemons> pokemons;
}
