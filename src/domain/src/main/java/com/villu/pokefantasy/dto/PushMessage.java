package com.villu.pokefantasy.dto;

/**
 * Aviso push. {@code path} (p. ej. {@code /leagues/l1/draft}) es la pantalla que abre en la web al pulsarlo;
 * {@code tag} hace que un aviso nuevo con la misma etiqueta sustituya al anterior en el navegador.
 * Android usa solo título y texto.
 */
public record PushMessage(String title, String body, String path, String tag) {

    /** "Te toca en el draft": abre el draft y sustituye al aviso de turno anterior de esa liga. */
    public static PushMessage draftTurn(String leagueId, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/draft", "draft-turn-" + leagueId);
    }

    /** Robo o intercambio: abre Equipos. Sin etiqueta: cada uno es un aviso distinto. */
    public static PushMessage teams(String leagueId, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/teams", null);
    }

    /** Cierre de ventana ({@code windowKey}: steal o swap): abre Equipos y sustituye al aviso anterior de esa ventana. */
    public static PushMessage window(String leagueId, String windowKey, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/teams", "window-" + windowKey + "-" + leagueId);
    }
}
