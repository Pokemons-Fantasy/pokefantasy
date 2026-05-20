package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueStatus;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.response.LeagueDetailResponse;
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
class GetLeagueDetailCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;

    private GetLeagueDetailCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetLeagueDetailCommandHandler(leagueRepository);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetLeagueDetailCommand("l1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_validLeague_mapsCorrectly() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setName("Kanto");
        league.setCreatedBy("ash");
        league.setStatus(LeagueStatus.ACTIVE);
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        LeagueDetailResponse result = handler.handle(new GetLeagueDetailCommand("l1"));

        assertThat(result.getId()).isEqualTo("l1");
        assertThat(result.getName()).isEqualTo("Kanto");
        assertThat(result.getCreatedBy()).isEqualTo("ash");
        assertThat(result.getStatus()).isEqualTo(LeagueStatus.ACTIVE);
        assertThat(result.getMembers()).hasSize(2);
        assertThat(result.getMembers().get(0).getUsername()).isEqualTo("ash");
        assertThat(result.getMembers().get(0).getLeagueRole()).isEqualTo(LeagueRole.ADMIN);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetLeagueDetailCommand.class);
    }
}
