package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
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

    private SetStealPriceCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String USERNAME  = "ash";
    private static final String POKEMON   = "charizard";

    @BeforeEach
    void setUp() {
        handler = new SetStealPriceCommandHandler(draftRepository, closedListRepository, leagueRepository);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_happyPath_deductsInvestmentAndSetsPrice() {
        // Tier S price = 300 → raise to 500 → invest 200
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
        assertThat(member.getCoinBalance()).isEqualTo(800); // 1000 - 200

        // customStealPrice updated on pick
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        DraftPick pick = draftCaptor.getValue().getPicks().stream()
                .filter(p -> POKEMON.equalsIgnoreCase(p.getPokemonName()))
                .findFirst().orElseThrow();
        assertThat(pick.getCustomStealPrice()).isEqualTo(newPrice);

        verify(leagueRepository).save(league);
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
        // tier price = 100, new = 400 → investment = 300, balance = 50 → throws
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
                .hasMessageContaining("suficientes monedas");
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
