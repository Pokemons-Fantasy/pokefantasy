package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUserResponse {
    private String username;
    /** Versión de su foto de perfil; {@code null} = sin foto. */
    private Long avatarVersion;
    /** Admin global (rol ADMIN): el front muestra el panel de moderación (L5). */
    private boolean admin;
    /** Nombre de su cuenta de Pokémon Showdown; {@code null} = sin vincular. */
    private String showdownName;
}
