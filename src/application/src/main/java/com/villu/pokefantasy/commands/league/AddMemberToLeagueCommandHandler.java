package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

@Service
public class AddMemberToLeagueCommandHandler implements CommandHandler<AddMemberToLeagueCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public AddMemberToLeagueCommandHandler(LeagueRepository leagueRepository,
                                            UserRepository userRepository,
                                            LeagueAdminGuard leagueAdminGuard) {
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public Void handle(AddMemberToLeagueCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        if (userRepository.findByUsername(command.targetUsername()) == null) {
            throw new IllegalArgumentException("User not found: " + command.targetUsername());
        }

        boolean alreadyMember = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()));
        if (alreadyMember) {
            throw new IllegalArgumentException("User '" + command.targetUsername() + "' is already a member of this league");
        }

        leagueRepository.addMember(command.leagueId(), new LeagueMember(command.targetUsername(), LeagueRole.USER));
        return null;
    }

    @Override
    public Class<AddMemberToLeagueCommand> commandType() {
        return AddMemberToLeagueCommand.class;
    }
}
