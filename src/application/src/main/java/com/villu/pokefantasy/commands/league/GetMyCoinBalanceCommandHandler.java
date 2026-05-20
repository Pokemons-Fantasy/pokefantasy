package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class GetMyCoinBalanceCommandHandler implements CommandHandler<GetMyCoinBalanceCommand, Integer> {

    private final LeagueRepository leagueRepository;

    public GetMyCoinBalanceCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public Integer handle(GetMyCoinBalanceCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        return league.getMembers().stream()
                .filter(m -> m.getUsername().equals(command.requestingUsername()))
                .findFirst()
                .map(m -> m.getCoinBalance())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User '" + command.requestingUsername() + "' is not a member of this league"));
    }

    @Override
    public Class<GetMyCoinBalanceCommand> commandType() {
        return GetMyCoinBalanceCommand.class;
    }
}
