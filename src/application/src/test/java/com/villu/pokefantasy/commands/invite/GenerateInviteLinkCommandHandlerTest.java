package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.InviteRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateInviteLinkCommandHandlerTest {

    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private InviteRepository inviteRepository;

    private GenerateInviteLinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GenerateInviteLinkCommandHandler(leagueAdminGuard, inviteRepository);
    }

    @Test
    void handle_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenOperationException("not admin"))
                .when(leagueAdminGuard).requireLeagueAdmin("l1", "ash");

        assertThatThrownBy(() -> handler.handle(new GenerateInviteLinkCommand("l1", "ash")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        doThrow(new IllegalArgumentException("League not found"))
                .when(leagueAdminGuard).requireLeagueAdmin("bad", "ash");

        assertThatThrownBy(() -> handler.handle(new GenerateInviteLinkCommand("bad", "ash")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_validAdmin_savesTokenAndReturnsIt() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        GenerateInviteLinkResponse response = handler.handle(new GenerateInviteLinkCommand("l1", "ash"));

        assertThat(response.token()).isNotBlank();

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(inviteRepository).save(tokenCaptor.capture(), eq("l1"), eq(48 * 3600L));
        assertThat(tokenCaptor.getValue()).isEqualTo(response.token());
    }

    @Test
    void handle_validAdmin_generatesUniqueTokens() {
        LeagueEntity league = new LeagueEntity();
        when(leagueAdminGuard.requireLeagueAdmin(anyString(), anyString())).thenReturn(league);

        GenerateInviteLinkResponse r1 = handler.handle(new GenerateInviteLinkCommand("l1", "ash"));
        GenerateInviteLinkResponse r2 = handler.handle(new GenerateInviteLinkCommand("l1", "ash"));

        assertThat(r1.token()).isNotEqualTo(r2.token());
    }

    @Test
    void handle_tokenTtlIs48Hours() {
        LeagueEntity league = new LeagueEntity();
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        handler.handle(new GenerateInviteLinkCommand("l1", "ash"));

        verify(inviteRepository).save(anyString(), eq("l1"), eq(172800L));
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GenerateInviteLinkCommand.class);
    }
}
