package com.villu.pokefantasy.commands.users;

/** Nombre con el que queda en el historial de las ligas quien borra su cuenta (L2). */
public final class DeletedUser {

    /** Lleva un espacio: ningún username válido ({@code ^[A-Za-z0-9_-]{3,20}$}) puede coincidir. */
    public static final String NAME = "Usuario eliminado";

    private DeletedUser() {}
}
