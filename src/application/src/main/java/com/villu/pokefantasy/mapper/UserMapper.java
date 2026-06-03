package com.villu.pokefantasy.mapper;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fcmTokens", ignore = true)
    UserEntity dtoToEntity(User user);

    User entityToDto(UserEntity userEntity);

    @Mapping(target = "fcmTokens", ignore = true)
    UserEntity updateUserWithPokemons(User user, List<Pokemons> pokemons);
}
