package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.league.TierPricingService;
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
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private GetBenchCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetBenchCommandHandler(closedListRepository, leagueRepository, userRepository, leagueMembershipGuard, new TierPricingService());
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetBenchCommand("l1", "ash")))
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

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1", "ash"));

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

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1", "ash"));

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

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1", "ash"));

        // null user contributes no owned pokemons, so all bench entries returned
        assertThat(bench).hasSize(1);
    }

    @Test
    void handle_returnsCorrectTierAndPriceForAllTiers() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of());
        league.setSettings(LeagueSettings.builder()
                .priceTierS(500).priceTierA(300).priceTierB(200).priceTierC(100).priceTierD(50)
                .build());
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(
                benchEntry("mewtwo",  150, Tier.S),
                benchEntry("dragonite", 149, Tier.A),
                benchEntry("vaporeon",  134, Tier.B),
                benchEntry("raichu",     26, Tier.C),
                benchEntry("caterpie",   10, Tier.D)
        ));

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1", "ash"));

        assertThat(bench).hasSize(5);
        assertThat(bench).anyMatch(e -> "S".equals(e.getTier()) && e.getPrice() == 500);
        assertThat(bench).anyMatch(e -> "A".equals(e.getTier()) && e.getPrice() == 300);
        assertThat(bench).anyMatch(e -> "B".equals(e.getTier()) && e.getPrice() == 200);
        assertThat(bench).anyMatch(e -> "C".equals(e.getTier()) && e.getPrice() == 100);
        assertThat(bench).anyMatch(e -> "D".equals(e.getTier()) && e.getPrice() == 50);
    }

    @Test
    void handle_nullSettings_returnsZeroPrices() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of());
        // settings deliberately null
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(
                List.of(benchEntry("pikachu", 25, Tier.A)));

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1", "ash"));

        assertThat(bench).hasSize(1);
        assertThat(bench.get(0).getPrice()).isEqualTo(0);
    }

    @Test
    void handle_nullPriceTierField_treatedAsZero() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of());
        // priceTierS not set → null getter
        league.setSettings(LeagueSettings.builder().build());
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(
                List.of(benchEntry("mewtwo", 150, Tier.S)));

        List<BenchEntryResponse> bench = handler.handle(new GetBenchCommand("l1", "ash"));

        assertThat(bench).hasSize(1);
        assertThat(bench.get(0).getPrice()).isEqualTo(0);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetBenchCommand.class);
    }

    // --- helper ---

    private ClosedListEntity benchEntry(String name, int id, Tier tier) {
        ClosedListEntity e = new ClosedListEntity();
        e.setPokemonId(id);
        e.setPokemonName(name);
        e.setTier(tier);
        e.setLeagueId("l1");
        return e;
    }
}
