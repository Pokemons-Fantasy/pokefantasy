package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CreateLeagueCommandHandler implements CommandHandler<CreateLeagueCommand, String> {

    private final LeagueRepository leagueRepository;

    public CreateLeagueCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public String handle(CreateLeagueCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("League name is required");
        }
        if (command.creatorUsername() == null || command.creatorUsername().isBlank()) {
            throw new IllegalArgumentException("Creator username is required");
        }

        LeagueEntity league = new LeagueEntity();
        league.setName(command.name());
        league.setCreatedBy(command.creatorUsername());
        league.setStatus(LeagueStatus.SETUP);
        league.setMembers(new ArrayList<>(List.of(new LeagueMember(command.creatorUsername(), LeagueRole.ADMIN, 0))));

        return leagueRepository.save(league).getId();
    }

    @Override
    public Class<CreateLeagueCommand> commandType() {
        return CreateLeagueCommand.class;
    }
}
