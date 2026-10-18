package com.villu.pokefantasy.league;

/**
 * Nombre de liga válido, igual al crearla y al renombrarla (L5): sin espacios sobrantes, entre 1 y 40 caracteres y
 * sin caracteres de control (un salto de línea falsearía líneas del log).
 */
public final class LeagueNamePolicy {

    static final int MAX_LENGTH = 40;

    private LeagueNamePolicy() {}

    /** El nombre recortado, o {@link IllegalArgumentException} si no cumple la regla. */
    public static String normalize(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El nombre de la liga debe tener entre 1 y " + MAX_LENGTH + " caracteres.");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "El nombre de la liga no puede tener saltos de línea ni caracteres de control.");
        }
        return trimmed;
    }

    /** Para el log: los nombres guardados antes de esta regla pueden tener caracteres de control. */
    public static String forLog(String name) {
        if (name == null) {
            return "null";
        }
        StringBuilder safe = new StringBuilder(name.length());
        name.chars().forEach(c -> safe.append(Character.isISOControl(c) ? '?' : (char) c));
        return safe.toString();
    }
}
