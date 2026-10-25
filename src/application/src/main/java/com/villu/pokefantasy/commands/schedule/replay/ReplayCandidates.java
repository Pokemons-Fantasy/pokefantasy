package com.villu.pokefantasy.commands.schedule.replay;

import com.villu.pokefantasy.commands.users.showdown.ShowdownIds;
import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;
import com.villu.pokefantasy.response.ReplayCandidateResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decide si un replay puede ser una partida del partido (jugadores, formato, semana, ganador, sin reutilizar) y
 * qué avisos tiene respecto a las alineaciones. Lo usan la lista de candidatos y el import, con las mismas reglas.
 */
public final class ReplayCandidates {

    public static final String REPLAY_BASE_URL = "https://replay.pokemonshowdown.com/";
    static final int MAX_WARNINGS = 12;
    /** Replays que se descargan enteros al buscar: una serie son hasta 3, así caben varias series. */
    public static final int MAX_SEARCHED = 15;
    private static final Pattern REPLAY_URL =
            Pattern.compile("^https://replay\\.pokemonshowdown\\.com/([a-z0-9-]+?)(?:\\.json)?/?$");

    private ReplayCandidates() {}

    private static final Pattern REPLAY_ID = Pattern.compile("^[a-z0-9-]{1,120}$");
    /** Ventana de la jornada: de lunes a lunes, más un día de margen para subir los replays del domingo. */
    public static final long WEEK_SECONDS = 8L * 24 * 3600;

    /** El id de un replay sin la contraseña de los privados ({@code ...-<contraseña>pw} → {@code ...-***pw}). */
    public static String redact(String replayId) {
        return replayId == null ? null : replayId.replaceAll("-[a-z0-9]+pw$", "-***pw");
    }

    /** Un id de replay de Showdown bien formado (los privados acaban en {@code -<contraseña>pw}). */
    public static boolean isReplayId(String id) {
        return id != null && REPLAY_ID.matcher(id).matches();
    }

    /** El id de un enlace de replay de Showdown (con o sin {@code .json}); vacío si no es uno. */
    public static Optional<String> replayId(String url) {
        if (url == null) return Optional.empty();
        Matcher m = REPLAY_URL.matcher(url.trim());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Un replay ya leído, con su log interpretado. */
    public record Evaluated(ShowdownReplay replay, ReplayLog log, String winnerUsername, Optional<String> reason,
                            List<String> warnings) {}

    public static Evaluated check(ReplayImportContext ctx, ShowdownReplay replay) {
        ReplayLog log = ReplayLog.parse(replay.log() == null ? "" : replay.log());
        Set<String> ids = replay.players().stream().map(ShowdownIds::toId).collect(Collectors.toSet());
        String winnerUsername = usernameOf(ctx, log.winner());
        String reason = null;
        if (!ids.equals(Set.of(ctx.player1Id(), ctx.player2Id()))) {
            reason = "No son los jugadores del partido";
        } else if (!ReplayLog.baseFormat(replay.formatid()).equals(ctx.baseFormat())) {
            reason = "Otro formato";
        } else if (ctx.fromEpoch() != null && (replay.uploadtime() < ctx.fromEpoch()
                || replay.uploadtime() >= ctx.fromEpoch() + WEEK_SECONDS)) {
            reason = "Fuera de la semana de la jornada";
        } else if (log.tie()) {
            reason = "Empate";
        } else if (winnerUsername == null) {
            // Sin |win| (abandonada o cortada) o con un ganador que no es ninguno de los dos.
            reason = "Partida sin terminar";
        } else if (ctx.usedReplayIds().contains(replay.id())) {
            reason = "Ya usado en otro partido";
        }
        return new Evaluated(replay, log, winnerUsername, Optional.ofNullable(reason), warnings(ctx, log));
    }

    /** Candidatos ordenados: series de la más reciente a la más antigua, cada una por partida; sueltos al final. */
    public static List<ReplayCandidateResponse> evaluate(ReplayImportContext ctx, List<ShowdownReplay> replays) {
        List<Evaluated> all = replays.stream().map(r -> check(ctx, r)).toList();
        Map<String, Long> newestOfSeries = all.stream()
                .filter(e -> e.log().seriesId() != null)
                .collect(Collectors.toMap(e -> e.log().seriesId(), e -> e.replay().uploadtime(), Math::max));
        Comparator<Evaluated> order = Comparator
                .comparing((Evaluated e) -> e.log().seriesId() == null)
                .thenComparing(e -> e.log().seriesId() == null ? 0L : -newestOfSeries.get(e.log().seriesId()))
                .thenComparing(e -> e.log().seriesId() == null ? "" : e.log().seriesId())
                .thenComparing(e -> e.log().gameNumber() == null ? 0 : e.log().gameNumber())
                .thenComparing(e -> -e.replay().uploadtime());
        return all.stream().sorted(order).map(ReplayCandidates::toResponse).toList();
    }

    private static ReplayCandidateResponse toResponse(Evaluated e) {
        return ReplayCandidateResponse.builder()
                .id(e.replay().id())
                .url(REPLAY_BASE_URL + e.replay().id())
                .uploadtime(e.replay().uploadtime())
                .winnerUsername(e.winnerUsername())
                .seriesId(e.log().seriesId())
                .gameNumber(e.log().gameNumber())
                .selectable(e.reason().isEmpty())
                .reason(e.reason().orElse(null))
                .warnings(e.warnings())
                .build();
    }

    /** El usuario de la liga cuyo {@code showdownId} es el de ese nombre de Showdown, o {@code null}. */
    private static String usernameOf(ReplayImportContext ctx, String showdownName) {
        if (showdownName == null) return null;
        String id = ShowdownIds.toId(showdownName);
        if (id.equals(ctx.player1Id())) return ctx.player1();
        if (id.equals(ctx.player2Id())) return ctx.player2();
        return null;
    }

    /** Lo que cada jugador llevó (Team Preview) y no está en su alineación declarada. Como mucho 12 avisos. */
    private static List<String> warnings(ReplayImportContext ctx, ReplayLog log) {
        List<String> warnings = new ArrayList<>();
        log.broughtBy().forEach((name, species) -> {
            String username = usernameOf(ctx, name);
            MatchLineup lineup = ctx.player1().equals(username) ? ctx.player1Lineup()
                    : ctx.player2().equals(username) ? ctx.player2Lineup() : null;
            if (lineup == null || lineup.getPokemonNames() == null) return;
            Set<String> declared = lineup.getPokemonNames().stream().map(ShowdownIds::toId).collect(Collectors.toSet());
            species.stream().filter(s -> !declared.contains(ShowdownIds.toId(s)))
                    .forEach(s -> warnings.add(username + " llevó " + s + ", que no está en su alineación."));
        });
        return warnings.size() <= MAX_WARNINGS ? warnings : warnings.subList(0, MAX_WARNINGS);
    }
}
