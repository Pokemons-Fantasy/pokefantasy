package com.villu.pokefantasy.ports;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;

import java.util.Optional;

/** Consultas a Pokémon Showdown (servidor oficial). Se llama fuera de la transacción del comando (ADR-007). */
public interface ShowdownPort {

    /**
     * Cuenta registrada con ese {@code userid}; vacío si no existe.
     *
     * @throws ShowdownUnavailableException si Showdown no responde
     */
    Optional<ShowdownUser> findUser(String showdownId);
}
