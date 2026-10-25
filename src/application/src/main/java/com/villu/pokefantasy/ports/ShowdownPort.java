package com.villu.pokefantasy.ports;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;

import java.util.List;
import java.util.Optional;

/** Consultas a Pokémon Showdown (servidor oficial). Se llama fuera de la transacción del comando (ADR-007). */
public interface ShowdownPort {

    /**
     * Cuenta registrada con ese {@code userid}; vacío si no existe.
     *
     * @throws ShowdownUnavailableException si Showdown no responde
     */
    Optional<ShowdownUser> findUser(String showdownId);

    /**
     * Texto exportado del equipo de un paste de pokepast.es; vacío si no existe.
     *
     * @throws ShowdownUnavailableException si pokepast.es no responde
     */
    Optional<String> getPokepaste(String pasteId);

    /**
     * Replays públicos entre los dos usuarios (por {@code userid}), más recientes primero; vacío si no hay.
     *
     * @throws ShowdownUnavailableException si Showdown no responde
     */
    List<ShowdownReplaySummary> searchReplays(String userId1, String userId2);

    /**
     * Un replay por su id (los privados llevan la contraseña en el id); vacío si no existe.
     *
     * @throws ShowdownUnavailableException si Showdown no responde
     */
    Optional<ShowdownReplay> getReplay(String replayId);
}
