package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.response.LeagueSettingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetLeagueSettingsCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private GetLeagueSettingsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetLeagueSettingsCommandHandler(leagueRepository, leagueMembershipGuard);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetLeagueSettingsCommand("l1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_settingsNull_returnsDefaults() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setSettings(null);
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        LeagueSettingsResponse response = handler.handle(new GetLeagueSettingsCommand("l1", "ash"));

        assertThat(response.getCoinsPerWin()).isEqualTo(100);
        assertThat(response.getCoinsPerLoss()).isEqualTo(50);
    }

    @Test
    void handle_settingsPresent_returnsStored() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setSettings(LeagueSettings.builder()
                .coinsPerWin(250)
                .coinsPerLoss(80)
                .build());
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        LeagueSettingsResponse response = handler.handle(new GetLeagueSettingsCommand("l1", "ash"));

        assertThat(response.getCoinsPerWin()).isEqualTo(250);
        assertThat(response.getCoinsPerLoss()).isEqualTo(80);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetLeagueSettingsCommand.class);
    }
}
