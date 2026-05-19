package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueStatus;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateLeagueCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;

    private CreateLeagueCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateLeagueCommandHandler(leagueRepository);
    }

    @Test
    void handle_blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateLeagueCommand("  ", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void handle_nullName_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateLeagueCommand(null, "ash")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_blankCreator_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateLeagueCommand("Liga", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username is required");
    }

    @Test
    void handle_validCommand_savesLeagueAndReturnsId() {
        LeagueEntity saved = new LeagueEntity();
        saved.setId("league-abc");
        when(leagueRepository.save(any())).thenReturn(saved);

        String id = handler.handle(new CreateLeagueCommand("Kanto League", "ash"));

        assertThat(id).isEqualTo("league-abc");

        ArgumentCaptor<LeagueEntity> captor = ArgumentCaptor.forClass(LeagueEntity.class);
        org.mockito.Mockito.verify(leagueRepository).save(captor.capture());
        LeagueEntity entity = captor.getValue();

        assertThat(entity.getName()).isEqualTo("Kanto League");
        assertThat(entity.getCreatedBy()).isEqualTo("ash");
        assertThat(entity.getStatus()).isEqualTo(LeagueStatus.SETUP);
        assertThat(entity.getMembers()).hasSize(1);
        assertThat(entity.getMembers().get(0).getUsername()).isEqualTo("ash");
        assertThat(entity.getMembers().get(0).getLeagueRole()).isEqualTo(LeagueRole.ADMIN);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(CreateLeagueCommand.class);
    }
}
