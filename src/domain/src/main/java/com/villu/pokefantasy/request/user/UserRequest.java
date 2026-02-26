package com.villu.pokefantasy.request.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UserRequest {

    private String username;
    private String password;
}
