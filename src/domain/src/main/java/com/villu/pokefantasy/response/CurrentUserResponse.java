package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUserResponse {
    private String username;
    /** Versión de su foto de perfil; {@code null} = sin foto. */
    private Long avatarVersion;
}
