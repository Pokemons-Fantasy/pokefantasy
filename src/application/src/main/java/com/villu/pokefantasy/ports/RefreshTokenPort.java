package com.villu.pokefantasy.ports;

import java.time.Duration;
import java.util.Optional;

/**
 * Tokens de refresco opacos y revocables. El JWT de acceso dura poco; mientras el refresh siga vivo
 * se emite uno nuevo sin volver a pedir la contraseña, y el logout lo revoca en el servidor.
 */
public interface RefreshTokenPort {

    /** Crea un token para {@code username}, o {@code null} si no se pudo guardar. */
    String issue(String username);

    /**
     * Usuario dueño del token si sigue vigente. Cada uso renueva su caducidad: la sesión solo expira
     * tras {@link #ttl()} sin actividad.
     */
    Optional<String> resolve(String token);

    void revoke(String token);

    Duration ttl();
}
