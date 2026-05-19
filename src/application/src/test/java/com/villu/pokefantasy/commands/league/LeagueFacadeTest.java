package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueFacadeTest {

    @Mock private Mediator mediator;

    private LeagueFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LeagueFacade(mediator);
    }

    @Test
    void createLeague_sendsCreateLeagueCommand() throws Exception {
        when(mediator.send(any(CreateLeagueCommand.class))).thenReturn("league-id");

        String id = facade.createLeague("Kanto", "ash");

        assertThat(id).isEqualTo("league-id");
        ArgumentCaptor<CreateLeagueCommand> captor = ArgumentCaptor.forClass(CreateLeagueCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Kanto");
        assertThat(captor.getValue().creatorUsername()).isEqualTo("ash");
    }

    @Test
    void addMember_sendsAddMemberCommand() throws Exception {
        facade.addMember("l1", "brock", "ash");

        ArgumentCaptor<AddMemberToLeagueCommand> captor = ArgumentCaptor.forClass(AddMemberToLeagueCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().targetUsername()).isEqualTo("brock");
        assertThat(captor.getValue().requestingUsername()).isEqualTo("ash");
    }

    @Test
    void getMyLeagues_sendsGetMyLeaguesCommand() throws Exception {
        when(mediator.send(any(GetMyLeaguesCommand.class))).thenReturn(java.util.List.of());

        facade.getMyLeagues("ash");

        ArgumentCaptor<GetMyLeaguesCommand> captor = ArgumentCaptor.forClass(GetMyLeaguesCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
    }

    @Test
    void getLeagueDetail_sendsGetLeagueDetailCommand() throws Exception {
        when(mediator.send(any(GetLeagueDetailCommand.class))).thenReturn(null);

        facade.getLeagueDetail("l1");

        ArgumentCaptor<GetLeagueDetailCommand> captor = ArgumentCaptor.forClass(GetLeagueDetailCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }

    @Test
    void getSettings_sendsGetLeagueSettingsCommand() throws Exception {
        when(mediator.send(any(GetLeagueSettingsCommand.class))).thenReturn(null);

        facade.getSettings("l1");

        ArgumentCaptor<GetLeagueSettingsCommand> captor = ArgumentCaptor.forClass(GetLeagueSettingsCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }

    @Test
    void updateSettings_sendsUpdateLeagueSettingsCommand() throws Exception {
        facade.updateSettings("l1", 200, 30, "ash");

        ArgumentCaptor<UpdateLeagueSettingsCommand> captor = ArgumentCaptor.forClass(UpdateLeagueSettingsCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().coinsPerWin()).isEqualTo(200);
        assertThat(captor.getValue().coinsPerLoss()).isEqualTo(30);
        assertThat(captor.getValue().requestingUsername()).isEqualTo("ash");
    }

    @Test
    void removeMember_sendsRemoveMemberCommand() throws Exception {
        facade.removeMember("l1", "brock", "ash");

        ArgumentCaptor<RemoveMemberFromLeagueCommand> captor = ArgumentCaptor.forClass(RemoveMemberFromLeagueCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().targetUsername()).isEqualTo("brock");
        assertThat(captor.getValue().requestingUsername()).isEqualTo("ash");
    }
}
