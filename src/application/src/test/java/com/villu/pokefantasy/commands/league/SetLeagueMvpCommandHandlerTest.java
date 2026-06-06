package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetLeagueMvpCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;

    private SetLeagueMvpCommandHandler handler;

    private static final String LEAGUE_ID = "l1";
    private static final String USERNAME  = "ash";

    @BeforeEach
    void setUp() {
        handler = new SetLeagueMvpCommandHandler(leagueRepository, draftRepository);
    }

    @Test
    void handle_validPokemon_persistsCustomMvp() {
        LeagueEntity league = leagueWith(USERNAME);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraftWith(USERNAME, "Pikachu", "Dragonite")));

        handler.handle(new SetLeagueMvpCommand(LEAGUE_ID, USERNAME, "dragonite")); // case-insensitive

        LeagueMember member = league.getMembers().get(0);
        assertThat(member.getCustomMvpPokemon()).isEqualTo("dragonite");
        verify(leagueRepository).save(league);
    }

    @Test
    void handle_pokemonNotInTeam_throwsIllegalArgument() {
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWith(USERNAME)));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraftWith(USERNAME, "Pikachu")));

        assertThatThrownBy(() -> handler.handle(new SetLeagueMvpCommand(LEAGUE_ID, USERNAME, "Dragonite")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dragonite");
    }

    @Test
    void handle_noDraft_throwsIllegalState() {
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWith(USERNAME)));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SetLeagueMvpCommand(LEAGUE_ID, USERNAME, "Pikachu")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWith(USERNAME)));
        DraftEntity draft = completedDraftWith(USERNAME, "Pikachu");
        draft.setStatus(com.villu.pokefantasy.dto.DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new SetLeagueMvpCommand(LEAGUE_ID, USERNAME, "Pikachu")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_nullPokemonName_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new SetLeagueMvpCommand(LEAGUE_ID, USERNAME, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pokémon");
    }

    @Test
    void handle_blankPokemonName_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new SetLeagueMvpCommand(LEAGUE_ID, USERNAME, "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pokémon");
    }

    // ── helpers ──

    private LeagueEntity leagueWith(String username) {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(new ArrayList<>(List.of(new LeagueMember(username, LeagueRole.USER, 500))));
        return league;
    }

    private DraftEntity completedDraftWith(String username, String... pokemonNames) {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(com.villu.pokefantasy.dto.DraftStatus.COMPLETED);
        List<DraftPick> picks = new ArrayList<>();
        for (int i = 0; i < pokemonNames.length; i++) {
            picks.add(new DraftPick(username, pokemonNames[i], i + 1, i + 1, null, null, null));
        }
        draft.setPicks(picks);
        return draft;
    }
}
