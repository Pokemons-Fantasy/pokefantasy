package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.response.LeagueResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetMyLeaguesCommandHandler implements CommandHandler<GetMyLeaguesCommand, List<LeagueResponse>> {

    private final LeagueRepository leagueRepository;

    public GetMyLeaguesCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public List<LeagueResponse> handle(GetMyLeaguesCommand command) {
        return leagueRepository.findByMemberUsername(command.username()).stream()
                .map(league -> LeagueResponse.builder()
                        .id(league.getId())
                        .name(league.getName())
                        .createdBy(league.getCreatedBy())
                        .memberCount(league.getMembers().size())
                        .status(league.getStatus())
                        .build())
                .toList();
    }

    @Override
    public Class<GetMyLeaguesCommand> commandType() {
        return GetMyLeaguesCommand.class;
    }
}
