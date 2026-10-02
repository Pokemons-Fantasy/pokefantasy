package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DraftTurnServiceTest {

    private final DraftTurnService service = new DraftTurnService();

    private static final DraftConfig CONFIG = DraftConfig.builder()
            .budget(300).priceS(200).priceA(150).priceB(100).priceC(60).priceD(30).snake(false).build();

    private static ClosedListEntity entry(String name, Tier tier) {
        ClosedListEntity e = new ClosedListEntity();
        e.setPokemonName(name);
        e.setTier(tier);
        return e;
    }

    private static DraftPick pick(String user, String name, Integer price) {
        DraftPick p = new DraftPick(user, name, 1, 1, Instant.now(), null, null);
        p.setPrice(price);
        return p;
    }

    private static DraftEntity draft(DraftConfig config, List<String> order, List<DraftPick> history) {
        DraftEntity d = new DraftEntity();
        d.setStatus(DraftStatus.IN_PROGRESS);
        d.setConfig(config);
        d.setTurnOrder(order);
        d.setCurrentTurnIndex(0);
        d.setCurrentRound(1);
        d.setPicks(new ArrayList<>(history));
        d.setDraftHistory(new ArrayList<>(history));
        return d;
    }

    @Test
    void maxTeamSize_usesSettingsOrDefaultTen() {
        LeagueEntity league = new LeagueEntity();
        assertThat(service.maxTeamSize(null)).isEqualTo(10);
        assertThat(service.maxTeamSize(league)).isEqualTo(10);
        league.setSettings(LeagueSettings.builder().maxTeamSize(0).build());
        assertThat(service.maxTeamSize(league)).isEqualTo(10);
        league.setSettings(LeagueSettings.builder().maxTeamSize(12).build());
        assertThat(service.maxTeamSize(league)).isEqualTo(12);
    }

    @Test
    void priceOf_usesTierPrice_zeroWithoutConfigOrTier() {
        DraftEntity withConfig = draft(CONFIG, List.of("ash"), List.of());
        assertThat(service.priceOf(withConfig, entry("mew", Tier.S))).isEqualTo(200);
        assertThat(service.priceOf(withConfig, entry("abra", Tier.D))).isEqualTo(30);
        assertThat(service.priceOf(withConfig, entry("ditto", null))).isZero();
        assertThat(service.priceOf(draft(null, List.of("ash"), List.of()), entry("mew", Tier.S))).isZero();
    }

    @Test
    void remainingBudget_subtractsHistoryPricesOfThatPlayer() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"),
                List.of(pick("ash", "mew", 200), pick("misty", "abra", 30), pick("ash", "old", null)));
        assertThat(service.remainingBudget(d, "ash")).isEqualTo(100);
        assertThat(service.remainingBudget(d, "misty")).isEqualTo(270);
        assertThat(service.remainingBudget(d, "brock")).isEqualTo(300);
    }

    @Test
    void remainingBudget_nullWithoutConfig() {
        assertThat(service.remainingBudget(draft(null, List.of("ash"), List.of()), "ash")).isNull();
    }

    @Test
    void available_excludesOwnedPokemonIgnoringCase() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "Mew", 200)));
        List<ClosedListEntity> pool = List.of(entry("mew", Tier.S), entry("abra", Tier.D));
        assertThat(service.available(d, pool)).extracting(ClosedListEntity::getPokemonName).containsExactly("abra");
    }

    @Test
    void canPick_exactBudget_true() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "mew", 200)));
        assertThat(service.canPick(d, "ash", List.of(entry("bulbasaur", Tier.B)), 10)).isTrue();
    }

    @Test
    void canPick_cannotAffordAnything_false() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "mew", 200), pick("ash", "abra", 30)));
        assertThat(service.canPick(d, "ash", List.of(entry("bulbasaur", Tier.B)), 10)).isFalse();
    }

    @Test
    void canPick_teamFull_false() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "abra", 30)));
        assertThat(service.canPick(d, "ash", List.of(entry("kadabra", Tier.D)), 1)).isFalse();
    }

    @Test
    void canPick_withoutConfig_onlyTeamSizeMatters() {
        DraftEntity d = draft(null, List.of("ash"), List.of());
        assertThat(service.canPick(d, "ash", List.of(), 10)).isTrue();
    }

    @Test
    void advance_linear_goesToNextPlayer() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty", "brock"), List.of());
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1);
        assertThat(d.getCurrentRound()).isEqualTo(1);
    }

    @Test
    void advance_linear_wrapsToFirstPlayerOfNextRound() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"), List.of());
        d.setCurrentTurnIndex(1);
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isZero();
        assertThat(d.getCurrentRound()).isEqualTo(2);
    }

    @Test
    void advance_skipsPlayerWhoCannotPay() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty", "brock"),
                List.of(pick("misty", "mew", 200), pick("misty", "mewtwo", 90)));
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(2); // misty (10 monedas) se salta
    }

    @Test
    void advance_snake_lastPlayerRepeatsAndRoundGoesBackwards() {
        DraftEntity d = draft(CONFIG.toBuilder().snake(true).build(), List.of("ash", "misty", "brock"), List.of());
        d.setCurrentTurnIndex(2);
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentRound()).isEqualTo(2);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(2);

        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1);
    }

    @Test
    void advance_snake_skipsBackwardsAndTurnsAtTheStart() {
        DraftEntity d = draft(CONFIG.toBuilder().snake(true).build(), List.of("ash", "misty", "brock"),
                List.of(pick("misty", "mew", 280)));
        d.setCurrentRound(2);
        d.setCurrentTurnIndex(2);
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isZero(); // misty (20 monedas) se salta hacia atrás
        assertThat(d.getCurrentRound()).isEqualTo(2);

        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentRound()).isEqualTo(3); // ash repite al empezar la ronda impar
        assertThat(d.getCurrentTurnIndex()).isZero();
    }

    @Test
    void advance_nobodyCanPay_completes() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"),
                List.of(pick("ash", "mew", 290), pick("misty", "mewtwo", 290)));
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void advance_noPokemonLeft_completes() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"), List.of());
        service.advance(d, List.of(), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void advance_pastLastRound_completes() {
        DraftEntity d = draft(null, List.of("ash", "misty"), List.of());
        d.setCurrentRound(10);
        d.setCurrentTurnIndex(1);
        service.advance(d, List.of(), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void removePlayer_currentPlayerInBudgetDraft_turnGoesToNextWhoCanPay() {
        DraftEntity d = draft(CONFIG, new ArrayList<>(List.of("ash", "misty", "brock", "gary")),
                List.of(pick("brock", "mew", 290)));
        d.setCurrentTurnIndex(1); // misty
        service.removePlayer(d, "misty", List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getTurnOrder()).containsExactly("ash", "brock", "gary");
        assertThat(d.getCurrentTurnIndex()).isEqualTo(2); // brock (10 monedas) se salta: gary
        assertThat(d.getCurrentRound()).isEqualTo(1);
        assertThat(d.getCurrentTurnStartedAt()).isNotNull();
    }

    @Test
    void removePlayer_currentPlayerInSnakeBackwardRound_turnGoesToPrevious() {
        DraftEntity d = draft(CONFIG.toBuilder().snake(true).build(), new ArrayList<>(List.of("ash", "misty", "brock")),
                List.of());
        d.setCurrentRound(2);
        d.setCurrentTurnIndex(1); // misty, ronda inversa: después va ash, no brock
        service.removePlayer(d, "misty", List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isZero();
        assertThat(d.getCurrentRound()).isEqualTo(2);
    }

    @Test
    void removePlayer_currentPlayerAndNobodyElseCanPay_completes() {
        DraftEntity d = draft(CONFIG, new ArrayList<>(List.of("ash", "misty")), List.of(pick("ash", "mew", 290)));
        d.setCurrentTurnIndex(1);
        service.removePlayer(d, "misty", List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void removePlayer_beforeCurrentShiftsIndex_unknownPlayerChangesNothing() {
        DraftEntity d = draft(CONFIG, new ArrayList<>(List.of("ash", "misty", "brock")), List.of());
        d.setCurrentTurnIndex(2);
        service.removePlayer(d, "ash", List.of(), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1);
        service.removePlayer(d, "gary", List.of(), 10);
        assertThat(d.getTurnOrder()).containsExactly("misty", "brock");
    }

    @Test
    void placeFirstTurn_startsWithFirstPlayerWhoCanPay() {
        DraftEntity d = draft(CONFIG.toBuilder().budget(50).build(), List.of("ash", "misty"),
                List.of(pick("ash", "abra", 30)));
        d.setCurrentTurnIndex(1);
        d.setCurrentRound(4);
        service.placeFirstTurn(d, List.of(entry("kadabra", Tier.D)), 10);
        assertThat(d.getCurrentRound()).isEqualTo(1);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1); // ash tiene 20 y lo más barato cuesta 30
    }

    @Test
    void placeFirstTurn_nobodyCanPay_completes() {
        DraftEntity d = draft(CONFIG.toBuilder().budget(10).build(), List.of("ash"), List.of());
        service.placeFirstTurn(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }
}
