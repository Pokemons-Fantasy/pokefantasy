package com.villu.pokefantasy.ports;

/**
 * Cuenta registrada en Pokémon Showdown: {@code username} es el nombre tal como lo muestra Showdown y
 * {@code userid} su forma normalizada (minúsculas y solo {@code [a-z0-9]}).
 */
public record ShowdownUser(String username, String userid) {}
