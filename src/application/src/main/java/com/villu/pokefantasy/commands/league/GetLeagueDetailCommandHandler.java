package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.league.CurrentDraftService;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueMemberResponse;
import org.springframework.stereotype.Service;

@Service
public class GetLeagueDetailCommandHandler implements CommandHandler<GetLeagueDetailCommand, LeagueDetailResponse> {

    private final LeagueRepository leagueRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final CurrentDraftService currentDraftService;

    public GetLeagueDetailCommandHandler(LeagueRepository leagueRepository, LeagueMembershipGuard leagueMembershipGuard,
                                         CurrentDraftService currentDraftService) {
        this.leagueRepository = leagueRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.currentDraftService = currentDraftService;
    }

    @Override
    public LeagueDetailResponse handle(GetLeagueDetailCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        var league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        return LeagueDetailResponse.builder()
                .id(league.getId())
                .name(league.getName())
                .createdBy(league.getCreatedBy())
                .status(league.getStatus())
                .draftStatus(currentDraftService.statusOf(league.getId()).orElse(null))
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
