package com.villu.pokefantasy.league;

/** Nombre de liga válido, igual al crearla y al renombrarla (L5): sin espacios sobrantes, entre 1 y 40 caracteres. */
public final class LeagueNamePolicy {

    static final int MAX_LENGTH = 40;

    private LeagueNamePolicy() {}

    /** El nombre recortado, o {@link IllegalArgumentException} si no cumple la regla. */
    public static String normalize(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El nombre de la liga debe tener entre 1 y " + MAX_LENGTH + " caracteres.");
        }
        return trimmed;
    }
}
