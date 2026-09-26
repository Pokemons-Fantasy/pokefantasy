package com.villu.pokefantasy.commands.users;

import java.nio.charset.StandardCharsets;

/** Requisitos de una contraseña nueva (registro y cambio de contraseña). */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** bcrypt ignora todo lo que pase de 72 bytes: una contraseña más larga daría falsa sensación de seguridad. */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** Lanza {@link IllegalArgumentException} con un mensaje para el usuario si no cumple los requisitos. */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 caracteres).");
        }
    }
}
