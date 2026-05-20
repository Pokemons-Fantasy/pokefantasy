package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
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
class GetMyCoinBalanceCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;

    private GetMyCoinBalanceCommandHandler handler;

    private static final String LEAGUE_ID = "l1";
    private static final String USERNAME = "ash";

    @BeforeEach
    void setUp() {
        handler = new GetMyCoinBalanceCommandHandler(leagueRepository);
    }

    @Test
    void handle_returnsMemberCoinBalance() {
        LeagueMember member = new LeagueMember(USERNAME, LeagueRole.ADMIN, 150);
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(List.of(member));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        int result = handler.handle(new GetMyCoinBalanceCommand(LEAGUE_ID, USERNAME));

        assertThat(result).isEqualTo(150);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetMyCoinBalanceCommand(LEAGUE_ID, USERNAME)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_memberNotInLeague_throwsIllegalArgument() {
        LeagueMember other = new LeagueMember("brock", LeagueRole.USER, 0);
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(List.of(other));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new GetMyCoinBalanceCommand(LEAGUE_ID, USERNAME)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetMyCoinBalanceCommand.class);
    }
}
