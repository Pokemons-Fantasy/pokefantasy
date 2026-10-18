package com.villu.pokefantasy.dto;

import java.time.Duration;

/**
 * Aviso push. {@code path} (p. ej. {@code /leagues/l1/draft}) es la pantalla que abre en la web al pulsarlo;
 * {@code tag} hace que un aviso nuevo con la misma etiqueta sustituya al anterior en el navegador.
 * {@code ttl}: si no se ha podido entregar en ese tiempo (móvil apagado o sin red), se descarta; {@code null}
 * deja el de FCM (4 semanas). {@code urgent}: se entrega al momento aunque el móvil esté en ahorro de batería.
 * Android usa título, texto, {@code ttl} y {@code urgent}.
 */
public record PushMessage(String title, String body, String path, String tag, Duration ttl, boolean urgent) {

    public PushMessage {
        // Un plazo ya vencido no debe romper el envío (FCM rechaza un ttl negativo): se intenta entregar al momento
        if (ttl != null && ttl.isNegative()) ttl = Duration.ZERO;
    }

    public PushMessage(String title, String body, String path, String tag) {
        this(title, body, path, tag, null, false);
    }

    /**
     * "Te toca en el draft": abre el draft, sustituye al aviso de turno anterior de esa liga, es urgente y
     * caduca con el turno ({@code ttl}).
     */
    public static PushMessage draftTurn(String leagueId, String title, String body, Duration ttl) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/draft", "draft-turn-" + leagueId, ttl, true);
    }

    /** Robo o intercambio: abre Equipos. Sin etiqueta: cada uno es un aviso distinto. */
    public static PushMessage teams(String leagueId, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/teams", null);
    }

    /**
     * Cierre de ventana ({@code windowKey}: steal o swap): abre Equipos, sustituye al aviso anterior de esa
     * ventana y caduca cuando cierra ({@code ttl}).
     */
    public static PushMessage window(String leagueId, String windowKey, String title, String body, Duration ttl) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/teams", "window-" + windowKey + "-" + leagueId,
                ttl, false);
    }
}
