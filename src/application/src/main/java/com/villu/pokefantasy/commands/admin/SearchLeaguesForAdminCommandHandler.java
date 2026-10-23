package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.response.AdminLeagueResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** Moderación (L5): ligas cuyo nombre contiene {@code q}, sin distinguir mayúsculas (como mucho 20). */
@Service
public class SearchLeaguesForAdminCommandHandler
        implements CommandHandler<SearchLeaguesForAdminCommand, List<AdminLeagueResponse>> {

    static final int LIMIT = 20;

    private final LeagueRepository leagueRepository;

    public SearchLeaguesForAdminCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public List<AdminLeagueResponse> handle(SearchLeaguesForAdminCommand command) {
        String q = command.q() == null ? "" : command.q().strip();
        if (q.length() < SearchUsersForAdminCommandHandler.MIN_QUERY_LENGTH) {
            return List.of();
        }
        return leagueRepository.findByNameContaining(q, LIMIT).stream()
                .map(league -> new AdminLeagueResponse(league.getId(), league.getName(), league.getMembers().size()))
                .toList();
    }

    @Override
    public Class<SearchLeaguesForAdminCommand> commandType() {
        return SearchLeaguesForAdminCommand.class;
    }
}
