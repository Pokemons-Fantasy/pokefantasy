package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

/** Un admin hace admin a otro miembro. Idempotente: si ya lo es, no cambia nada. */
@Service
public class PromoteMemberToAdminCommandHandler implements CommandHandler<PromoteMemberToAdminCommand, Void> {

    private final LeagueAdminGuard leagueAdminGuard;
    private final LeagueRepository leagueRepository;

    public PromoteMemberToAdminCommandHandler(LeagueAdminGuard leagueAdminGuard, LeagueRepository leagueRepository) {
        this.leagueAdminGuard = leagueAdminGuard;
        this.leagueRepository = leagueRepository;
    }

    @Override
    public Void handle(PromoteMemberToAdminCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        LeagueMember target = league.getMembers().stream()
                .filter(m -> m.getUsername().equals(command.targetUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + command.targetUsername() + "' no es miembro de esta liga"));

        if (target.getLeagueRole() == LeagueRole.ADMIN) {
            return null;
        }

        target.setLeagueRole(LeagueRole.ADMIN);
        leagueRepository.save(league);
        return null;
    }

    @Override
    public Class<PromoteMemberToAdminCommand> commandType() {
        return PromoteMemberToAdminCommand.class;
    }
}
