package com.villu.pokefantasy.mapper;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserEntity dtoToEntity(User user);
    User entityToDto(UserEntity userEntity);
}
