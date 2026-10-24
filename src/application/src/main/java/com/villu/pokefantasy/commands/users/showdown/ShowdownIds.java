package com.villu.pokefantasy.commands.users.showdown;

import java.util.Locale;

/** Ids de Pokémon Showdown. */
public final class ShowdownIds {

    private ShowdownIds() {}

    /** Mismo {@code toID} que Showdown: minúsculas y solo {@code [a-z0-9]}; {@code null} → {@code ""}. */
    public static String toId(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
