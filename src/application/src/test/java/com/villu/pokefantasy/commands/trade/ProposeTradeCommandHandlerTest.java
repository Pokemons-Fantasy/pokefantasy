package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposeTradeCommandHandlerTest {

    @Mock private TradeRepository tradeRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private LeagueRepository leagueRepository;

    private ProposeTradeCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProposeTradeCommandHandler(tradeRepository, draftRepository, leagueRepository);
    }

    @Test
    void handle_validProposal_savesPendingTrade() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 1000),
                new LeagueMember("brock", LeagueRole.USER, 1000)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        handler.handle(new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 150));

        ArgumentCaptor<TradeEntity> captor = ArgumentCaptor.forClass(TradeEntity.class);
        verify(tradeRepository).save(captor.capture());
        TradeEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TradeStatus.PENDING);
        assertThat(saved.getProposer()).isEqualTo("ash");
        assertThat(saved.getResponder()).isEqualTo("brock");
        assertThat(saved.getProposerPokemonName()).isEqualTo("pikachu");
        assertThat(saved.getProposerPokemonId()).isEqualTo(25);
        assertThat(saved.getResponderPokemonName()).isEqualTo("onix");
        assertThat(saved.getResponderPokemonId()).isEqualTo(95);
        assertThat(saved.getCoinsOffered()).isEqualTo(150);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getResolvedAt()).isNull();
    }

    @Test
    void handle_proposerEqualsResponder_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "ASH", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ti mismo");
    }

    @Test
    void handle_negativeCoins_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft is completed");
    }

    @Test
    void handle_noDraft_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_responderNotMember_throwsIllegalArgument() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.USER, 1000)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es miembro");
    }

    @Test
    void handle_proposerPokemonNotOwned_throwsIllegalArgument() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        // ash doesn't have pikachu
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "bulbasaur", 1, 1, Instant.now(), null, null),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 1000),
                new LeagueMember("brock", LeagueRole.USER, 1000)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está en el equipo");
    }

    @Test
    void handle_responderPokemonNotOwned_throwsIllegalArgument() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        // brock doesn't have onix
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("brock", "geodude", 74, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 1000),
                new LeagueMember("brock", LeagueRole.USER, 1000)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_pokemonLocked_throwsIllegalState() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        // ash's pikachu is locked until 1 hour from now
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null,
                        Instant.now().plus(1, ChronoUnit.HOURS)),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 1000),
                new LeagueMember("brock", LeagueRole.USER, 1000)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bloqueado");
    }

    @Test
    void handle_insufficientBalance_throwsIllegalState() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        LeagueEntity league = new LeagueEntity();
        // ash only has 50 coins but coinsOffered = 150
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.USER, 50),
                new LeagueMember("brock", LeagueRole.USER, 1000)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(
                new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 150)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(ProposeTradeCommand.class);
    }
}
