package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateDraftConfigCommandHandlerTest {

    @Mock private DraftSetupGuard draftSetupGuard;
    @Mock private DraftRepository draftRepository;

    private UpdateDraftConfigCommandHandler handler;
    private final DraftEntity draft = new DraftEntity();
    private final LeagueEntity league = new LeagueEntity();

    @BeforeEach
    void setUp() {
        handler = new UpdateDraftConfigCommandHandler(draftSetupGuard, draftRepository, new TurnOrderPolicy());
        draft.setStatus(DraftStatus.PENDING);
        draft.setTurnOrder(List.of("ash", "misty"));
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0))));
    }

    private void inSetup() {
        when(draftSetupGuard.requireDraftInSetup("l1", "ash")).thenReturn(new DraftSetupGuard.DraftSetup(league, draft));
    }

    private static DraftConfig config(Integer budget, Integer priceD) {
        return DraftConfig.builder().budget(budget).priceS(200).priceA(150).priceB(100).priceC(60).priceD(priceD)
                .snake(null).build();
    }

    @Test
    void handle_valid_savesConfigAndOrder_snakeNullIsFalse() {
        inSetup();
        handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, 0), List.of("misty", "ash")));

        verify(draftRepository).save(draft);
        assertThat(draft.getConfig().getBudget()).isEqualTo(800);
        assertThat(draft.getConfig().getPriceD()).isZero();
        assertThat(draft.getConfig().getSnake()).isFalse();
        assertThat(draft.getTurnOrder()).containsExactly("misty", "ash");
    }

    @Test
    void handle_nullTurnOrder_keepsCurrentOrder() {
        inSetup();
        handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, 30), null));
        assertThat(draft.getTurnOrder()).containsExactly("ash", "misty");
    }

    @Test
    void handle_invalidBudgetOrPrices_throws() {
        inSetup();
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(0, 30), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El presupuesto tiene que ser mayor que 0");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(null, 30), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El presupuesto tiene que ser mayor que 0");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, null), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Indica el precio de todos los tiers");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, -1), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Los precios de los tiers no pueden ser negativos");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El presupuesto tiene que ser mayor que 0");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(UpdateDraftConfigCommand.class);
    }
}
