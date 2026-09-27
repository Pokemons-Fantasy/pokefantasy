package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.league.CurrentDraftService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.response.LeagueResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetMyLeaguesCommandHandler implements CommandHandler<GetMyLeaguesCommand, List<LeagueResponse>> {

    private final LeagueRepository leagueRepository;
    private final CurrentDraftService currentDraftService;

    public GetMyLeaguesCommandHandler(LeagueRepository leagueRepository, CurrentDraftService currentDraftService) {
        this.leagueRepository = leagueRepository;
        this.currentDraftService = currentDraftService;
    }

    @Override
    public List<LeagueResponse> handle(GetMyLeaguesCommand command) {
        List<LeagueEntity> leagues = leagueRepository.findByMemberUsername(command.username());
        var draftStatuses = currentDraftService.statusByLeague(leagues.stream().map(LeagueEntity::getId).toList());
        return leagues.stream()
                .map(league -> LeagueResponse.builder()
                        .id(league.getId())
                        .name(league.getName())
                        .createdBy(league.getCreatedBy())
                        .memberCount(league.getMembers().size())
                        .status(league.getStatus())
                        .draftStatus(draftStatuses.get(league.getId()))
                        .build())
                .toList();
    }

    @Override
    public Class<GetMyLeaguesCommand> commandType() {
        return GetMyLeaguesCommand.class;
    }
}
