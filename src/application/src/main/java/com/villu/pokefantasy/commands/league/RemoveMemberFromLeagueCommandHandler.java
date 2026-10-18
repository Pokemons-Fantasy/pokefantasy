package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMemberRemoval;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class RemoveMemberFromLeagueCommandHandler implements CommandHandler<RemoveMemberFromLeagueCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRemoval leagueMemberRemoval;

    public RemoveMemberFromLeagueCommandHandler(LeagueRepository leagueRepository,
                                                LeagueMemberRemoval leagueMemberRemoval) {
        this.leagueRepository = leagueRepository;
        this.leagueMemberRemoval = leagueMemberRemoval;
    }

    @Override
    public Void handle(RemoveMemberFromLeagueCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        boolean isSelfLeave = command.requestingUsername().equals(command.targetUsername());
        boolean requesterIsAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.requestingUsername()) && m.getLeagueRole() == LeagueRole.ADMIN);

        if (!isSelfLeave && !requesterIsAdmin) {
            throw new ForbiddenOperationException("Solo un admin puede expulsar a otros miembros");
        }

        boolean targetExists = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()));
        if (!targetExists) {
            throw new IllegalArgumentException("'" + command.targetUsername() + "' no es miembro de esta liga");
        }

        boolean targetIsAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()) && m.getLeagueRole() == LeagueRole.ADMIN);
        if (targetIsAdmin) {
            long adminCount = league.getMembers().stream().filter(m -> m.getLeagueRole() == LeagueRole.ADMIN).count();
            if (adminCount <= 1) {
                throw new IllegalStateException("La liga no puede quedarse sin admin");
            }
        }

        leagueMemberRemoval.remove(league, command.targetUsername());
        return null;
    }

    @Override
    public Class<RemoveMemberFromLeagueCommand> commandType() {
        return RemoveMemberFromLeagueCommand.class;
    }
}
