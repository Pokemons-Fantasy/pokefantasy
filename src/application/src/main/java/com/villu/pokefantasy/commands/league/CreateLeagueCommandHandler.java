package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueStatus;
import com.villu.pokefantasy.league.LeagueNamePolicy;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CreateLeagueCommandHandler implements CommandHandler<CreateLeagueCommand, String> {

    /** Ligas que puede crear un usuario en total. */
    static final int MAX_LEAGUES_PER_USER = 10;

    private final LeagueRepository leagueRepository;

    public CreateLeagueCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public String handle(CreateLeagueCommand command) {
        String name = LeagueNamePolicy.normalize(command.name());
        if (command.creatorUsername() == null || command.creatorUsername().isBlank()) {
            throw new IllegalArgumentException("Falta el usuario que crea la liga.");
        }
        // Las ligas no se pueden borrar: el tope es de por vida y evita llenar la base de datos.
        if (leagueRepository.countByCreatedBy(command.creatorUsername()) >= MAX_LEAGUES_PER_USER) {
            throw new IllegalStateException("Has llegado al máximo de " + MAX_LEAGUES_PER_USER + " ligas creadas.");
        }

        LeagueEntity league = new LeagueEntity();
        league.setName(name);
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
