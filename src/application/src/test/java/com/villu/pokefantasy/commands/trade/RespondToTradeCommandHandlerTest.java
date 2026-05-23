package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RespondToTradeCommandHandlerTest {

    @Mock private TradeRepository tradeRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private JornadaWindowService jornadaWindowService;
    @Mock private ActivityEventRepository activityEventRepository;

    private RespondToTradeCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RespondToTradeCommandHandler(
                tradeRepository, draftRepository, scheduleRepository,
                leagueRepository, userRepository, closedListRepository, jornadaWindowService,
                activityEventRepository);
    }

    private TradeEntity pendingTrade() {
        return TradeEntity.builder()
                .id("t1").leagueId("l1").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25)
                .responderPokemonName("onix").responderPokemonId(95)
                .coinsOffered(100).status(TradeStatus.PENDING).createdAt(Instant.now())
                .build();
    }

    private UserEntity userWith(String username, String pokemonName, int pokemonId) {
        UserEntity u = new UserEntity();
        u.setName(username);
        Pokemons p = new Pokemons();
        p.setId(pokemonId);
        p.setName(pokemonName);
        p.setLeagueId("l1");
        u.setPokemons(new ArrayList<>(List.of(p)));
        return u;
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Test
    void handle_reject_marksTradeRejected() {
        TradeEntity trade = pendingTrade();
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        handler.handle(new RespondToTradeCommand("l1", "t1", "brock", false));

        ArgumentCaptor<TradeEntity> captor = ArgumentCaptor.forClass(TradeEntity.class);
        verify(tradeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TradeStatus.REJECTED);
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
        verifyNoInteractions(draftRepository);
    }

    // ── Accept — full swap ────────────────────────────────────────────────────

    @Test
    void handle_accept_executesSwapAndLocksBothPicks() {
        TradeEntity trade = pendingTrade();
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        DraftPick ashPick = new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null);
        DraftPick brockPick = new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null);
        draft.setPicks(new ArrayList<>(List.of(ashPick, brockPick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        ScheduleEntity schedule = new ScheduleEntity();
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);
        when(jornadaWindowService.getActiveJornada(schedule))
                .thenReturn(Optional.of(new Jornada(3, new ArrayList<>(), null)));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 500),
                new LeagueMember("brock", LeagueRole.USER, 200)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        when(userRepository.findByUsername("ash")).thenReturn(userWith("ash", "pikachu", 25));
        when(userRepository.findByUsername("brock")).thenReturn(userWith("brock", "onix", 95));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(anyString(), eq("l1")))
                .thenReturn(Optional.empty());
        when(tradeRepository.findPendingByLeagueId("l1")).thenReturn(List.of(trade));

        handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true));

        // picks intercambiados y bloqueados
        assertThat(ashPick.getUsername()).isEqualTo("brock");
        assertThat(brockPick.getUsername()).isEqualTo("ash");
        assertThat(ashPick.getLockedUntilRound()).isEqualTo(3);
        assertThat(brockPick.getLockedUntilRound()).isEqualTo(3);
        // monedas: ash paga 100, brock recibe 100
        assertThat(league.getMembers().get(0).getCoinBalance()).isEqualTo(400);
        assertThat(league.getMembers().get(1).getCoinBalance()).isEqualTo(300);
        // trade marcado ACCEPTED
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.ACCEPTED);
        verify(draftRepository).save(draft);
        verify(leagueRepository).save(league);
        verify(userRepository, times(2)).updateUserWithPokemons(any());
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test
    void handle_tradeNotFound_throwsIllegalArgument() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trade not found");
    }

    @Test
    void handle_tradeNotPending_throwsIllegalState() {
        TradeEntity trade = pendingTrade();
        trade.setStatus(TradeStatus.ACCEPTED);
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya no está pendiente");
    }

    @Test
    void handle_notResponder_throwsForbidden() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));

        // ash is the proposer, not the responder
        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "ash", true)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_accept_draftNotCompleted_throwsIllegalState() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_accept_noSchedule_throwsIllegalState() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>());
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No schedule");
    }

    @Test
    void handle_accept_swapWindowClosed_throwsIllegalState() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>());
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));
        ScheduleEntity schedule = new ScheduleEntity();
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ventana de intercambios");
    }

    @Test
    void handle_accept_proposerPokemonMoved_cancelsTradeAndThrows() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        // only brock's onix present, ash's pikachu is gone
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));
        ScheduleEntity schedule = new ScheduleEntity();
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);
        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 500),
                new LeagueMember("brock", LeagueRole.USER, 200)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya no es válida");
        verify(tradeRepository).save(any(TradeEntity.class));
    }

    @Test
    void handle_accept_pickLocked_throwsIllegalState() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        // ash's pikachu locked until round 3
        DraftPick ashPick = new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, 3);
        DraftPick brockPick = new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null);
        draft.setPicks(new ArrayList<>(List.of(ashPick, brockPick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        // schedule with jornada 3 containing PENDING match
        Match match = new Match("m1", "ash", "brock", null, MatchStatus.PENDING);
        Jornada jornada = new Jornada(3, new ArrayList<>(List.of(match)), null);
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setJornadas(new ArrayList<>(List.of(jornada)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 500),
                new LeagueMember("brock", LeagueRole.USER, 200)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bloqueado");
    }

    @Test
    void handle_accept_insufficientProposerBalance_throwsIllegalState() {
        // pendingTrade has coinsOffered=100; ash has only 50
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        DraftPick ashPick = new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null);
        DraftPick brockPick = new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null);
        draft.setPicks(new ArrayList<>(List.of(ashPick, brockPick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));
        ScheduleEntity schedule = new ScheduleEntity();
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);
        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 50),
                new LeagueMember("brock", LeagueRole.USER, 200)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas");
    }

    @Test
    void handle_accept_cancelsConflictingPendingTrades() {
        TradeEntity trade = pendingTrade();
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        DraftPick ashPick = new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null);
        DraftPick brockPick = new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null);
        draft.setPicks(new ArrayList<>(List.of(ashPick, brockPick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        ScheduleEntity schedule = new ScheduleEntity();
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);
        when(jornadaWindowService.getActiveJornada(schedule))
                .thenReturn(Optional.of(new Jornada(3, new ArrayList<>(), null)));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 500),
                new LeagueMember("brock", LeagueRole.USER, 200)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        when(userRepository.findByUsername("ash")).thenReturn(userWith("ash", "pikachu", 25));
        when(userRepository.findByUsername("brock")).thenReturn(userWith("brock", "onix", 95));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(anyString(), eq("l1")))
                .thenReturn(Optional.empty());

        // otherTrade also references pikachu — should be cancelled
        TradeEntity otherTrade = TradeEntity.builder()
                .id("t2").leagueId("l1").proposer("misty").responder("ash")
                .proposerPokemonName("staryu").proposerPokemonId(120)
                .responderPokemonName("pikachu").responderPokemonId(25)
                .coinsOffered(0).status(TradeStatus.PENDING).createdAt(Instant.now())
                .build();
        when(tradeRepository.findPendingByLeagueId("l1")).thenReturn(List.of(trade, otherTrade));

        handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true));

        assertThat(otherTrade.getStatus()).isEqualTo(TradeStatus.CANCELLED);
        verify(tradeRepository).save(otherTrade);
    }

    @Test
    void handle_accept_savesTradeCompletedActivityEvent() {
        TradeEntity trade = pendingTrade();
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        DraftPick ashPick = new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null);
        DraftPick brockPick = new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null);
        draft.setPicks(new ArrayList<>(List.of(ashPick, brockPick)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        ScheduleEntity schedule = new ScheduleEntity();
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);
        when(jornadaWindowService.getActiveJornada(schedule))
                .thenReturn(Optional.of(new Jornada(3, new ArrayList<>(), null)));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 500),
                new LeagueMember("brock", LeagueRole.USER, 200)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        when(userRepository.findByUsername("ash")).thenReturn(userWith("ash", "pikachu", 25));
        when(userRepository.findByUsername("brock")).thenReturn(userWith("brock", "onix", 95));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(anyString(), eq("l1")))
                .thenReturn(Optional.empty());
        when(tradeRepository.findPendingByLeagueId("l1")).thenReturn(List.of(trade));

        handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true));

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(captor.capture());
        ActivityEventEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(ActivityEventType.TRADE_COMPLETED);
        assertThat(saved.getActorUsername()).isEqualTo("ash");
        assertThat(saved.getTargetUsername()).isEqualTo("brock");
        assertThat(saved.getPokemonName()).isEqualTo("pikachu");
        assertThat(saved.getPokemonName2()).isEqualTo("onix");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(RespondToTradeCommand.class);
    }
}
