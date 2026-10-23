package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.league.LeagueNamePolicy;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Moderación (L5): el admin global cambia el nombre de una liga ofensiva. Solo llega por /v1/admin. */
@Service
@Slf4j
public class RenameLeagueCommandHandler implements CommandHandler<RenameLeagueCommand, Void> {

    private final LeagueRepository leagueRepository;

    public RenameLeagueCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public Void handle(RenameLeagueCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));
        String name = LeagueNamePolicy.normalize(command.name());
        log.info("Admin {} renamed league {} from '{}' to '{}'", command.adminUsername(), command.leagueId(),
                LeagueNamePolicy.forLog(league.getName()), name);
        league.setName(name);
        leagueRepository.save(league);
        return null;
    }

    @Override
    public Class<RenameLeagueCommand> commandType() {
        return RenameLeagueCommand.class;
    }
}
