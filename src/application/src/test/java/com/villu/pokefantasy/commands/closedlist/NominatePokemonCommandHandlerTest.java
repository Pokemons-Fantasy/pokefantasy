package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NominatePokemonCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private CachePort cachePort;
    @Mock private PokemonApiPort pokemonApiPort;
    @Mock private DraftRepository draftRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private NominatePokemonCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NominatePokemonCommandHandler(closedListRepository, cachePort, pokemonApiPort, draftRepository, leagueMembershipGuard);
    }

    @Test
    void handle_notLeagueMember_propagatesForbidden() {
        when(leagueMembershipGuard.requireMember("l1", "ash"))
                .thenThrow(new ForbiddenOperationException("not a member"));

        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("  ", "pikachu", "l1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullPokemonName_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", null, "l1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_blankLeagueId_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", "pikachu", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_draftNotPending_throwsIllegalState() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nominations are closed");
    }

    @Test
    void handle_pokemonAlreadyInClosedList_throwsIllegalArgument() {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());
        when(closedListRepository.existsByPokemonNameAndLeagueId("pikachu", "l1")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in the closed list");
    }

    @Test
    void handle_maxNominationsReached_throwsIllegalArgument() {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());
        when(closedListRepository.existsByPokemonNameAndLeagueId("pikachu", "l1")).thenReturn(false);
        when(closedListRepository.countByNominatedByAndLeagueId("ash", "l1")).thenReturn(16L);

        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void handle_pokemonNotInCache_throwsIllegalArgument() {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());
        when(closedListRepository.existsByPokemonNameAndLeagueId("pikachu", "l1")).thenReturn(false);
        when(closedListRepository.countByNominatedByAndLeagueId("ash", "l1")).thenReturn(0L);
        when(cachePort.getPokemon("pokemons")).thenReturn(List.of(
                new PokemonCacheDto("url", "bulbasaur", 1)));

        assertThatThrownBy(() -> handler.handle(new NominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void handle_validNomination_savesEntry() throws Exception {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());
        when(closedListRepository.existsByPokemonNameAndLeagueId("pikachu", "l1")).thenReturn(false);
        when(closedListRepository.countByNominatedByAndLeagueId("ash", "l1")).thenReturn(0L);
        when(cachePort.getPokemon("pokemons")).thenReturn(List.of(
                new PokemonCacheDto("https://pokeapi.co/api/v2/pokemon/25/", "pikachu", 25)));
        Pokemons poke = new Pokemons(25, "pikachu", null, null, null, null, null, null);
        when(pokemonApiPort.fetchPokemonById("https://pokeapi.co/api/v2/pokemon/25/", "pikachu")).thenReturn(poke);

        handler.handle(new NominatePokemonCommand("ash", "pikachu", "l1"));

        ArgumentCaptor<com.villu.pokefantasy.repository.entity.ClosedListEntity> captor =
                ArgumentCaptor.forClass(com.villu.pokefantasy.repository.entity.ClosedListEntity.class);
        verify(closedListRepository).save(captor.capture());
        assertThat(captor.getValue().getPokemonName()).isEqualTo("pikachu");
        assertThat(captor.getValue().getNominatedBy()).isEqualTo("ash");
        assertThat(captor.getValue().getLeagueId()).isEqualTo("l1");
        assertThat(captor.getValue().getPokemonId()).isEqualTo(25);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(NominatePokemonCommand.class);
    }
}
