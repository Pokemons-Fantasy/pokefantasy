package com.villu.pokefantasy.request.schedule;

import lombok.Data;

import java.util.List;

@Data
public class DeclareLineupRequest {
    /** Los Pokémon del partido (6, o todo el equipo si tiene menos). */
    private List<String> pokemonNames;
    /** Enlace al equipo completo; obligatorio con lista abierta, ignorado con lista cerrada. */
    private String pasteUrl;
}
