package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import com.villu.pokefantasy.response.BenchEntryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBenchCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;

    private GetBenchCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetBenchCommandHandler(closedListRepository, leagueRepository, userRepository);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetBenchCommand("l1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_noOwnedPokemons_returnsFullClosedList() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        UserEntity user = new UserEntity();
        user.setPokemons(List.of());
        when(userRepository.findByUsername("ash")).thenReturn(user);

        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonId(25);
        entry.setPokemonName("pikachu");
        entry.setSprite("sprite");
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(entry));

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1"));

        assertThat(bench).hasSize(1);
        assertThat(bench.get(0).getPokemonName()).isEqualTo("pikachu");
    }

    @Test
    void handle_ownedPokemonsFiltered_returnsOnlyBench() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        Pokemons ownedPokemon = new Pokemons(25, "pikachu", null, null, null, null, null, "l1");
        UserEntity user = new UserEntity();
        user.setPokemons(List.of(ownedPokemon));
        when(userRepository.findByUsername("ash")).thenReturn(user);

        ClosedListEntity pikachu = new ClosedListEntity();
        pikachu.setPokemonId(25);
        pikachu.setPokemonName("pikachu");
        ClosedListEntity bulbasaur = new ClosedListEntity();
        bulbasaur.setPokemonId(1);
        bulbasaur.setPokemonName("bulbasaur");
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(pikachu, bulbasaur));

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1"));

        assertThat(bench).hasSize(1);
        assertThat(bench.get(0).getPokemonName()).isEqualTo("bulbasaur");
    }

    @Test
    void handle_userIsNull_treatedAsNoPokemons() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        when(userRepository.findByUsername("ash")).thenReturn(null);

        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonId(25);
        entry.setPokemonName("pikachu");
        entry.setSprite("sprite");
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(entry));

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1"));

        // null user contributes no owned pokemons, so all bench entries returned
        assertThat(bench).hasSize(1);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetBenchCommand.class);
    }
}
