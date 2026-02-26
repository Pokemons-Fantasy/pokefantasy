package com.villu.pokefantasy.repository.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "users")
public class UserEntity{
    private String id;
    private String name;
    private String password;
}
