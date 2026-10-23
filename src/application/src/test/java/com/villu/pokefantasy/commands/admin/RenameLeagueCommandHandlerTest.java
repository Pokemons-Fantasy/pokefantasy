package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenameLeagueCommandHandlerTest {

    private final LeagueRepository leagueRepository = mock(LeagueRepository.class);
    private final RenameLeagueCommandHandler handler = new RenameLeagueCommandHandler(leagueRepository);

    @Test
    void rename_savesTheNormalizedName() {
        LeagueEntity league = new LeagueEntity();
        league.setName("Insulto");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        handler.handle(new RenameLeagueCommand("root", "l1", "  Liga Kanto "));

        assertThat(league.getName()).isEqualTo("Liga Kanto");
        verify(leagueRepository).save(league);
        assertThat(handler.commandType()).isEqualTo(RenameLeagueCommand.class);
    }

    @Test
    void rename_unknownLeague_isNotFound() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RenameLeagueCommand("root", "l1", "Kanto")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Liga no encontrada");
    }

    @Test
    void rename_invalidName_doesNotSave() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(new LeagueEntity()));

        assertThatThrownBy(() -> handler.handle(new RenameLeagueCommand("root", "l1", "x".repeat(41))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(leagueRepository, never()).save(any());
    }
}
