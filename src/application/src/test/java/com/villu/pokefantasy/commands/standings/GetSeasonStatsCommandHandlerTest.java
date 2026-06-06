package com.villu.pokefantasy.commands.standings;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse.PlayerSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSeasonStatsCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;

    private GetSeasonStatsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetSeasonStatsCommandHandler(scheduleRepository, leagueRepository, draftRepository);
    }

    // ── Caso 1: sin schedule → todos a cero, mvpPokemon de draft si existe ──────

    @Test
    void handle_noSchedule_allZeros_mvpFromDraft() {
        LeagueEntity league = leagueWithMembers("ash", "brock");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        DraftEntity draft = new DraftEntity();
        draft.setLeagueId("l1");
        DraftPick pick = new DraftPick("ash", "Pikachu", 25, 1, null, null, null);
        draft.setDraftHistory(new ArrayList<>(List.of(pick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));

        assertThat(response.players()).hasSize(2);
        PlayerSeasonStats ash = findPlayer(response, "ash");
        PlayerSeasonStats brock = findPlayer(response, "brock");

        assertThat(ash.wins()).isZero();
        assertThat(ash.losses()).isZero();
        assertThat(ash.played()).isZero();
        assertThat(ash.winPct()).isZero();
        assertThat(ash.currentStreak()).isZero();
        assertThat(ash.mvpPokemon()).isEqualTo("Pikachu");

        assertThat(brock.mvpPokemon()).isNull();
    }

    // ── Caso 2: W/L/winPct calculados correctamente (3W 1L → 75%) ──────────────

    @Test
    void handle_completedMatches_winsLossesWinPct() {
        LeagueEntity league = leagueWithMembers("ash", "brock", "misty");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        // ash: 3 partidos con brock/misty/brock — gana 3, pierde 1 (en jornada 2)
        Match m1 = new Match("m1", "ash", "brock",  "ash",   MatchStatus.COMPLETED);
        Match m2 = new Match("m2", "ash", "misty",  "ash",   MatchStatus.COMPLETED);
        Match m3 = new Match("m3", "ash", "brock",  "ash",   MatchStatus.COMPLETED);
        Match m4 = new Match("m4", "misty", "ash",  "misty", MatchStatus.COMPLETED);
        Jornada j1 = new Jornada(1, new ArrayList<>(List.of(m1, m2, m3)), null);
        Jornada j2 = new Jornada(2, new ArrayList<>(List.of(m4)), null);
        schedule.setJornadas(new ArrayList<>(List.of(j1, j2)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        PlayerSeasonStats ash = findPlayer(response, "ash");

        assertThat(ash.wins()).isEqualTo(3);
        assertThat(ash.losses()).isEqualTo(1);
        assertThat(ash.played()).isEqualTo(4);
        assertThat(ash.winPct()).isEqualTo(75);
    }

    // ── Caso 3: streak positiva (últimos 3 ganados → +3) ────────────────────────

    @Test
    void handle_positiveStreak_lastThreeWins() {
        LeagueEntity league = leagueWithMembers("ash", "brock");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        ScheduleEntity schedule = new ScheduleEntity();
        // jornadas 1,2,3 — ash gana las tres
        Match m1 = new Match("m1", "ash", "brock", "ash", MatchStatus.COMPLETED);
        Match m2 = new Match("m2", "ash", "brock", "ash", MatchStatus.COMPLETED);
        Match m3 = new Match("m3", "ash", "brock", "ash", MatchStatus.COMPLETED);
        schedule.setJornadas(new ArrayList<>(List.of(
                new Jornada(1, new ArrayList<>(List.of(m1)), null),
                new Jornada(2, new ArrayList<>(List.of(m2)), null),
                new Jornada(3, new ArrayList<>(List.of(m3)), null)
        )));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        assertThat(findPlayer(response, "ash").currentStreak()).isEqualTo(3);
    }

    // ── Caso 4: streak negativa (últimos 2 perdidos → -2) ────────────────────────

    @Test
    void handle_negativeStreak_lastTwoLosses() {
        LeagueEntity league = leagueWithMembers("ash", "brock");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        ScheduleEntity schedule = new ScheduleEntity();
        Match m1 = new Match("m1", "ash", "brock", "brock", MatchStatus.COMPLETED);
        Match m2 = new Match("m2", "ash", "brock", "brock", MatchStatus.COMPLETED);
        schedule.setJornadas(new ArrayList<>(List.of(
                new Jornada(1, new ArrayList<>(List.of(m1)), null),
                new Jornada(2, new ArrayList<>(List.of(m2)), null)
        )));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        assertThat(findPlayer(response, "ash").currentStreak()).isEqualTo(-2);
    }

    // ── Caso 5: WLLW en orden → currentStreak=1 (último ganado, corta en la L) ──

    @Test
    void handle_streakCut_WLLWPattern() {
        LeagueEntity league = leagueWithMembers("ash", "brock");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        ScheduleEntity schedule = new ScheduleEntity();
        // j1=W, j2=L, j3=L, j4=W  → más reciente es W, pero antes hay L → streak=1
        Match mW1 = new Match("m1", "ash", "brock", "ash",   MatchStatus.COMPLETED);
        Match mL1 = new Match("m2", "ash", "brock", "brock", MatchStatus.COMPLETED);
        Match mL2 = new Match("m3", "ash", "brock", "brock", MatchStatus.COMPLETED);
        Match mW2 = new Match("m4", "ash", "brock", "ash",   MatchStatus.COMPLETED);
        schedule.setJornadas(new ArrayList<>(List.of(
                new Jornada(1, new ArrayList<>(List.of(mW1)), null),
                new Jornada(2, new ArrayList<>(List.of(mL1)), null),
                new Jornada(3, new ArrayList<>(List.of(mL2)), null),
                new Jornada(4, new ArrayList<>(List.of(mW2)), null)
        )));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        assertThat(findPlayer(response, "ash").currentStreak()).isEqualTo(1);
    }

    // ── Caso 6: sin draft → mvpPokemon=null ─────────────────────────────────────

    @Test
    void handle_noDraft_mvpPokemonNull() {
        LeagueEntity league = leagueWithMembers("ash");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        assertThat(findPlayer(response, "ash").mvpPokemon()).isNull();
    }

    // ── Caso 7: con draft → mvpPokemon = primer pick (round más bajo) ───────────

    @Test
    void handle_draft_mvpIsFirstPickByRound() {
        LeagueEntity league = leagueWithMembers("ash");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        DraftEntity draft = new DraftEntity();
        draft.setLeagueId("l1");
        // round 2 primero en la lista, round 1 segundo → el de round 1 debe ganar
        DraftPick pick2 = new DraftPick("ash", "Charizard", 6,  2, null, null, null);
        DraftPick pick1 = new DraftPick("ash", "Pikachu",   25, 1, null, null, null);
        draft.setDraftHistory(new ArrayList<>(List.of(pick2, pick1)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        assertThat(findPlayer(response, "ash").mvpPokemon()).isEqualTo("Pikachu");
    }

    // ── Caso 8: orden — jugador con más wins aparece primero ─────────────────────

    @Test
    void handle_sortOrder_mostWinsFirst() {
        LeagueEntity league = leagueWithMembers("ash", "brock", "misty");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        ScheduleEntity schedule = new ScheduleEntity();
        // misty: 2W, ash: 1W, brock: 0W
        Match m1 = new Match("m1", "misty", "ash",   "misty", MatchStatus.COMPLETED);
        Match m2 = new Match("m2", "misty", "brock", "misty", MatchStatus.COMPLETED);
        Match m3 = new Match("m3", "ash",   "brock", "ash",   MatchStatus.COMPLETED);
        Jornada j = new Jornada(1, new ArrayList<>(List.of(m1, m2, m3)), null);
        schedule.setJornadas(new ArrayList<>(List.of(j)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));
        List<PlayerSeasonStats> players = response.players();

        assertThat(players.get(0).username()).isEqualTo("misty");
        assertThat(players.get(1).username()).isEqualTo("ash");
        assertThat(players.get(2).username()).isEqualTo("brock");
    }

    // ── Caso 9: customMvpPokemon tiene prioridad sobre el primer pick del draft ───

    @Test
    void handle_customMvpSet_overridesAutoMvp() {
        // Liga con miembro "ash" cuyo customMvpPokemon = "Mewtwo"
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        LeagueMember member = new LeagueMember("ash", LeagueRole.ADMIN, 0);
        member.setCustomMvpPokemon("Mewtwo");
        league.setMembers(new ArrayList<>(List.of(member)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        // Draft history tiene "Pikachu" como primer pick (auto-MVP sin customización)
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId("l1");
        DraftPick pick = new DraftPick("ash", "Pikachu", 25, 1, null, null, null);
        draft.setDraftHistory(new ArrayList<>(List.of(pick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        SeasonStatsResponse response = handler.handle(new GetSeasonStatsCommand("l1"));

        // Debe devolver "Mewtwo" (custom), no "Pikachu" (auto)
        assertThat(response.players()).hasSize(1);
        assertThat(response.players().get(0).mvpPokemon()).isEqualTo("Mewtwo");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private LeagueEntity leagueWithMembers(String... usernames) {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        List<LeagueMember> members = new ArrayList<>();
        for (String u : usernames) {
            members.add(new LeagueMember(u, LeagueRole.USER, 0));
        }
        league.setMembers(members);
        return league;
    }

    private PlayerSeasonStats findPlayer(SeasonStatsResponse response, String username) {
        return response.players().stream()
                .filter(p -> p.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Player not found: " + username));
    }
}
