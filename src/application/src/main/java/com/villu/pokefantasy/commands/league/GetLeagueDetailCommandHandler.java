package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueMemberResponse;
import org.springframework.stereotype.Service;

@Service
public class GetLeagueDetailCommandHandler implements CommandHandler<GetLeagueDetailCommand, LeagueDetailResponse> {

    private final LeagueRepository leagueRepository;

    public GetLeagueDetailCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public LeagueDetailResponse handle(GetLeagueDetailCommand command) {
        var league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        return LeagueDetailResponse.builder()
                .id(league.getId())
                .name(league.getName())
                .createdBy(league.getCreatedBy())
                .status(league.getStatus())
                .members(league.getMembers().stream()
                        .map(m -> LeagueMemberResponse.builder()
                                .username(m.getUsername())
                                .leagueRole(m.getLeagueRole())
                                .build())
                        .toList())
                .build();
    }

    @Override
    public Class<GetLeagueDetailCommand> commandType() {
        return GetLeagueDetailCommand.class;
    }
}
