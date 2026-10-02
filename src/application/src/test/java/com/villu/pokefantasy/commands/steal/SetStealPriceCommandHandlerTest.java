package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetStealPriceCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private ActivityEventRepository activityEventRepository;

    private SetStealPriceCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String USERNAME  = "ash";
    private static final String POKEMON   = "charizard";

    @BeforeEach
    void setUp() {
        handler = new SetStealPriceCommandHandler(draftRepository, leagueRepository, new LeagueMemberService(),
                new StealClauseService(closedListRepository, new TierPricingService()), activityEventRepository);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_happyPath_chargesHalfTheIncreaseAndSetsPrice() {
        // Tier S price = 300 → raise to 500 → each coin adds 2 → invest 100
        int currentTierPrice = 300;
        int newPrice = 500;
        int initialBalance = 1000;

        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null); // no customStealPrice
        LeagueEntity league = leagueWithMember(initialBalance);
        league.setSettings(LeagueSettings.builder().priceTierS(currentTierPrice).build());

        ClosedListEntity entry = closedListEntry(POKEMON, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, newPrice));

        // Investment deducted
        LeagueMember member = getMember(league);
        assertThat(member.getCoinBalance()).isEqualTo(900); // 1000 - 100

        // customStealPrice updated on pick
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        DraftPick pick = draftCaptor.getValue().getPicks().stream()
                .filter(p -> POKEMON.equalsIgnoreCase(p.getPokemonName()))
                .findFirst().orElseThrow();
        assertThat(pick.getCustomStealPrice()).isEqualTo(newPrice);

        verify(leagueRepository).save(league);
    }

    @Test
    void handle_oddIncrease_roundsCostUpAndClauseToTwiceThePaidCoins() {
        // 300 → 501 pedido: coste ceil(201/2) = 101, la cláusula queda en 300 + 2·101 = 502
        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null);
        LeagueEntity league = leagueWithMember(1000);
        league.setSettings(LeagueSettings.builder().priceTierS(300).build());
        ClosedListEntity entry = closedListEntry(POKEMON, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, 501));

        assertThat(getMember(league).getCoinBalance()).isEqualTo(899);
        assertThat(draft.getPicks().get(0).getCustomStealPrice()).isEqualTo(502);
    }

    @Test
    void handle_raise_savesClauseRaisedEventWithInvestment() {
        DraftEntity draft = draftWithPick(USERNAME, POKEMON, 400);
        LeagueEntity league = leagueWithMember(1000);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, 600));

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(captor.capture());
        ActivityEventEntity event = captor.getValue();
        assertThat(event.getType()).isEqualTo(ActivityEventType.CLAUSE_RAISED);
        assertThat(event.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(event.getActorUsername()).isEqualTo(USERNAME);
        assertThat(event.getPokemonName()).isEqualTo(POKEMON);
        assertThat(event.getCoinsAmount()).isEqualTo(100); // 400 → 600: invierte 100
        assertThat(event.getCreatedAt()).isNotNull();
    }

    // ── New price not higher than current ─────────────────────────────────────

    @Test
    void handle_newPriceLowerThanCurrent_throws() {
        // customStealPrice already 500 → try to set 300 → blocked
        DraftEntity draft = draftWithPick(USERNAME, POKEMON, 500);
        LeagueEntity league = leagueWithMember(9999);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, 300)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor que el precio actual");
    }

    @Test
    void handle_newPriceEqualToCurrent_throws() {
        // tier price = 200, new = 200 → not strictly higher → blocked
        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null);
        LeagueEntity league = leagueWithMember(9999);
        league.setSettings(LeagueSettings.builder().priceTierA(200).build());
        ClosedListEntity entry = closedListEntry(POKEMON, Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, 200)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor que el precio actual");
    }

    // ── Insufficient coins ────────────────────────────────────────────────────

    @Test
    void handle_insufficientCoinsForInvestment_throws() {
        // tier price = 100, new = 400 → investment = 150, balance = 50 → throws
        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null);
        LeagueEntity league = leagueWithMember(50);
        league.setSettings(LeagueSettings.builder().priceTierB(100).build());
        ClosedListEntity entry = closedListEntry(POKEMON, Tier.B);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, 400)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas")
                .hasMessageContaining("invertir 150");
    }

    // ── Negative price ────────────────────────────────────────────────────────

    @Test
    void handle_negativePrice_throws() {
        // Throws before any repository call → no stubs needed
        assertThatThrownBy(() -> handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser negativo");
    }

    // ── Tier C price used ─────────────────────────────────────────────────────

    @Test
    void handle_tierCPokemon_raisesPrice() {
        // Covers case C in priceForTier switch
        int tierCPrice = 50;
        int newPrice = 120;

        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null);
        LeagueEntity league = leagueWithMember(1000);
        league.setSettings(LeagueSettings.builder().priceTierC(tierCPrice).build());
        ClosedListEntity entry = closedListEntry(POKEMON, Tier.C);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, newPrice));

        LeagueMember member = getMember(league);
        assertThat(member.getCoinBalance()).isEqualTo(965); // 1000 - 35
    }

    // ── Tier D price used ─────────────────────────────────────────────────────

    @Test
    void handle_tierDPokemon_raisesPrice() {
        // Covers case D in priceForTier switch
        int tierDPrice = 25;
        int newPrice = 60;

        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null);
        LeagueEntity league = leagueWithMember(500);
        league.setSettings(LeagueSettings.builder().priceTierD(tierDPrice).build());
        ClosedListEntity entry = closedListEntry(POKEMON, Tier.D);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, newPrice));

        LeagueMember member = getMember(league);
        assertThat(member.getCoinBalance()).isEqualTo(482); // 500 - ceil(35/2)
    }

    // ── Concurrencia: draft.save falla → se propaga sin compensar ───────

    @Test
    void handle_draftSaveConflict_propagatesWithoutCompensating() {
        int currentTierPrice = 300;
        int newPrice = 500;
        int initialBalance = 1000;

        DraftEntity draft = draftWithPick(USERNAME, POKEMON, null);
        LeagueEntity league = leagueWithMember(initialBalance);
        league.setSettings(LeagueSettings.builder().priceTierS(currentTierPrice).build());
        ClosedListEntity entry = closedListEntry(POKEMON, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        doThrow(new OptimisticLockingFailureException("stale draft")).when(draftRepository).save(draft);

        assertThatThrownBy(() -> handler.handle(new SetStealPriceCommand(LEAGUE_ID, USERNAME, POKEMON, newPrice)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Sin compensaciones manuales: el conflicto se propaga intacto para que la transacción
        // del mediator deshaga todas las escrituras y reintente el comando.
        verify(leagueRepository, times(1)).save(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(SetStealPriceCommand.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DraftEntity draftWithPick(String username, String pokemon, Integer customStealPrice) {
        DraftEntity draft = new DraftEntity();
        draft.setId("draft-1");
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.COMPLETED);
        DraftPick pick = new DraftPick(username, pokemon, 6, 1, Instant.now(), customStealPrice, null);
        draft.setPicks(new ArrayList<>(List.of(pick)));
        draft.setTurnOrder(List.of(username));
        return draft;
    }

    private LeagueEntity leagueWithMember(int balance) {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(new ArrayList<>(List.of(new LeagueMember(USERNAME, LeagueRole.USER, balance))));
        return league;
    }

    private ClosedListEntity closedListEntry(String name, Tier tier) {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonName(name);
        entry.setPokemonId(6);
        entry.setLeagueId(LEAGUE_ID);
        entry.setTier(tier);
        return entry;
    }

    private LeagueMember getMember(LeagueEntity league) {
        return league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername()))
                .findFirst().orElseThrow();
    }
}
