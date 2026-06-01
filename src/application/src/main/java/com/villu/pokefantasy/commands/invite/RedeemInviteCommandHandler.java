package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.InviteRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

@Service
public class RedeemInviteCommandHandler
        implements CommandHandler<RedeemInviteCommand, String> {

    private final InviteRepository inviteRepository;
    private final LeagueRepository leagueRepository;

    public RedeemInviteCommandHandler(InviteRepository inviteRepository, LeagueRepository leagueRepository) {
        this.inviteRepository = inviteRepository;
        this.leagueRepository = leagueRepository;
    }

    @Override
    public String handle(RedeemInviteCommand command) {
        String leagueId = inviteRepository.findLeagueId(command.token());
        if (leagueId == null) throw new IllegalArgumentException("Invalid or expired invite token");

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found"));

        boolean alreadyMember = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.username()));
        if (alreadyMember) throw new IllegalStateException("Already a member of this league");

        leagueRepository.addMember(leagueId, new LeagueMember(command.username(), LeagueRole.USER, 0));
        inviteRepository.delete(command.token());
        return leagueId;
    }

    @Override
    public Class<RedeemInviteCommand> commandType() {
        return RedeemInviteCommand.class;
    }
}
