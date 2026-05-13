package com.villu.pokefantasy.dto.users;

import com.villu.pokefantasy.dto.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class User {

    private String id;
    private String name;
    private String password;
    private Role role;
}
