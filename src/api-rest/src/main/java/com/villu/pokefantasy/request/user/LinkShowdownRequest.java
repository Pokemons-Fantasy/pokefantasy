package com.villu.pokefantasy.request.user;

import lombok.Data;

@Data
public class LinkShowdownRequest {
    /** Nombre de la cuenta de Showdown tal como lo escribe el usuario. */
    private String name;
}
