package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.InviteRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GenerateInviteLinkCommandHandler
        implements CommandHandler<GenerateInviteLinkCommand, GenerateInviteLinkResponse> {

    private final LeagueAdminGuard leagueAdminGuard;
    private final InviteRepository inviteRepository;

    public GenerateInviteLinkCommandHandler(LeagueAdminGuard leagueAdminGuard, InviteRepository inviteRepository) {
        this.leagueAdminGuard = leagueAdminGuard;
        this.inviteRepository = inviteRepository;
    }

    @Override
    public GenerateInviteLinkResponse handle(GenerateInviteLinkCommand command) {
        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());
        String token = UUID.randomUUID().toString();
        inviteRepository.save(token, command.leagueId(), 48 * 3600L);
        return new GenerateInviteLinkResponse(token);
    }

    @Override
    public Class<GenerateInviteLinkCommand> commandType() {
        return GenerateInviteLinkCommand.class;
    }
}
