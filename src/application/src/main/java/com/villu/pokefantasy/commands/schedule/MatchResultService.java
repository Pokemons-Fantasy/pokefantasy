package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Lo común a registrar, corregir y deshacer el resultado de un partido: localizar el partido, validar
 * al ganador y dar (o devolver) las monedas de la jornada, dejando rastro en el feed de actividad.
 */
@Service
public class MatchResultService {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;
    private final ActivityEventRepository activityEventRepository;

    public MatchResultService(LeagueRepository leagueRepository,
                              DraftRepository draftRepository,
                              ActivityEventRepository activityEventRepository) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
        this.activityEventRepository = activityEventRepository;
    }

    ScheduleEntity.Match findMatch(ScheduleEntity schedule, String matchId) {
        if (schedule.getJornadas() != null) {
            for (ScheduleEntity.Jornada jornada : schedule.getJornadas()) {
                if (jornada.getMatches() == null) continue;
                for (ScheduleEntity.Match m : jornada.getMatches()) {
                    if (matchId.equals(m.getId())) {
                        return m;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Match not found: " + matchId);
    }

    int findRoundNumber(ScheduleEntity schedule, String matchId) {
        if (schedule.getJornadas() == null) return 0;
        for (ScheduleEntity.Jornada jornada : schedule.getJornadas()) {
            if (jornada.getMatches() == null) continue;
            for (ScheduleEntity.Match m : jornada.getMatches()) {
                if (matchId.equals(m.getId())) {
                    return jornada.getRoundNumber();
                }
            }
        }
        return 0;
    }

    /** Valida que {@code winner} juega el partido y devuelve al perdedor. */
    String requireParticipant(ScheduleEntity.Match match, String winner) {
        if (winner == null || (!winner.equals(match.getPlayer1()) && !winner.equals(match.getPlayer2()))) {
            throw new IllegalArgumentException("Winner '" + winner + "' is not a participant of this match");
        }
        return winner.equals(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1();
    }

    /**
     * Validates forfeit conditions:
     * - If the declared winner has 0 Pokémon in this league → reject (probably a mistake; suggest the loser)
     * - If the loser has 0 Pokémon → forfeit confirmed, no action needed
     */
    void requireWinnerHasTeam(String leagueId, String winner, String loser) {
        boolean winnerHasPokemons = draftRepository.findLatestByLeagueId(leagueId)
                .map(draft -> draft.teamSize(winner) > 0)
                .orElse(false);

        if (!winnerHasPokemons) {
            throw new IllegalArgumentException(
                    "El ganador declarado '" + winner + "' no tiene Pokémon en esta liga. " +
                    "¿Quisiste decir '" + loser + "'?");
        }
    }

    /**
     * Marca el partido como ganado por {@code winner}, da las monedas de victoria/derrota según los
     * ajustes actuales (y las anota en el partido) y registra los eventos. Guarda la liga; el
     * calendario lo guarda quien llama.
     */
    void award(LeagueEntity league, ScheduleEntity.Match match, String winner, String loser, int roundNumber) {
        LeagueSettings settings = league.getSettings();
        int coinsWin  = (settings != null && settings.getCoinsPerWin()  != null) ? settings.getCoinsPerWin()  : 0;
        int coinsLoss = (settings != null && settings.getCoinsPerLoss() != null) ? settings.getCoinsPerLoss() : 0;

        match.setWinnerUsername(winner);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinnerCoins(coinsWin);
        match.setLoserCoins(coinsLoss);

        addCoins(league, winner, coinsWin);
        addCoins(league, loser, coinsLoss);
        leagueRepository.save(league);

        Instant now = Instant.now();
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(league.getId())
                .type(ActivityEventType.MATCH_RESULT)
                .actorUsername(winner)
                .targetUsername(loser)
                .roundNumber(roundNumber)
                .createdAt(now)
                .build());
        saveCoinEvent(league.getId(), ActivityEventType.COIN_EARNED, winner, coinsWin, roundNumber, now);
        saveCoinEvent(league.getId(), ActivityEventType.COIN_EARNED, loser, coinsLoss, roundNumber, now);
    }

    /**
     * Deshace un resultado registrado: devuelve las monedas que se dieron (las anotadas en el partido;
     * en resultados antiguos sin anotar, las de los ajustes actuales) y deja el partido pendiente.
     * El saldo puede quedar negativo si ya se gastaron. No guarda nada: quien llama guarda la liga
     * (o vuelve a llamar a {@link #award}) y el calendario.
     */
    void revoke(LeagueEntity league, ScheduleEntity.Match match, int roundNumber) {
        String winner = match.getWinnerUsername();
        String loser = winner.equals(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1();
        LeagueSettings settings = league.getSettings();
        int coinsWin = match.getWinnerCoins() != null ? match.getWinnerCoins()
                : (settings != null && settings.getCoinsPerWin() != null ? settings.getCoinsPerWin() : 0);
        int coinsLoss = match.getLoserCoins() != null ? match.getLoserCoins()
                : (settings != null && settings.getCoinsPerLoss() != null ? settings.getCoinsPerLoss() : 0);

        addCoins(league, winner, -coinsWin);
        addCoins(league, loser, -coinsLoss);

        match.setWinnerUsername(null);
        match.setStatus(MatchStatus.PENDING);
        match.setWinnerCoins(null);
        match.setLoserCoins(null);

        Instant now = Instant.now();
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(league.getId())
                .type(ActivityEventType.MATCH_RESULT_REVERTED)
                .actorUsername(winner)
                .targetUsername(loser)
                .roundNumber(roundNumber)
                .createdAt(now)
                .build());
        // Contrapartida de los COIN_EARNED del registro: el historial de monedas cuadra con el saldo.
        saveCoinEvent(league.getId(), ActivityEventType.COIN_REVOKED, winner, coinsWin, roundNumber, now);
        saveCoinEvent(league.getId(), ActivityEventType.COIN_REVOKED, loser, coinsLoss, roundNumber, now);
    }

    private static void addCoins(LeagueEntity league, String username, int amount) {
        for (LeagueMember m : league.getMembers()) {
            if (m.getUsername().equals(username)) {
                m.setCoinBalance(m.getCoinBalance() + amount);
            }
        }
    }

    private void saveCoinEvent(String leagueId, ActivityEventType type, String username, int coins,
                               int roundNumber, Instant now) {
        if (coins <= 0) return;
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(type)
                .actorUsername(username)
                .coinsAmount(coins)
                .roundNumber(roundNumber)
                .createdAt(now)
                .build());
    }
}
