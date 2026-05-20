package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueStatus;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.response.LeagueResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMyLeaguesCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;

    private GetMyLeaguesCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetMyLeaguesCommandHandler(leagueRepository);
    }

    @Test
    void handle_noLeagues_returnsEmptyList() {
        when(leagueRepository.findByMemberUsername("ash")).thenReturn(List.of());
        assertThat(handler.handle(new GetMyLeaguesCommand("ash"))).isEmpty();
    }

    @Test
    void handle_withLeagues_mapsCorrectly() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setName("Kanto");
        league.setCreatedBy("ash");
        league.setStatus(LeagueStatus.ACTIVE);
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember("ash", com.villu.pokefantasy.dto.LeagueRole.ADMIN, 0),
                new LeagueMember("brock", com.villu.pokefantasy.dto.LeagueRole.USER, 0)
        )));
        when(leagueRepository.findByMemberUsername("ash")).thenReturn(List.of(league));

        List<LeagueResponse> result = handler.handle(new GetMyLeaguesCommand("ash"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("l1");
        assertThat(result.get(0).getName()).isEqualTo("Kanto");
        assertThat(result.get(0).getMemberCount()).isEqualTo(2);
        assertThat(result.get(0).getStatus()).isEqualTo(LeagueStatus.ACTIVE);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetMyLeaguesCommand.class);
    }
}
