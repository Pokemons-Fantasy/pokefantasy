package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.response.ActivityEventResponse;
import com.villu.pokefantasy.response.ActivityFeedResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetActivityFeedCommandHandler implements CommandHandler<GetActivityFeedCommand, ActivityFeedResponse> {

    private final ActivityEventRepository activityEventRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;

    public GetActivityFeedCommandHandler(ActivityEventRepository activityEventRepository, LeagueMembershipGuard leagueMembershipGuard) {
        this.activityEventRepository = activityEventRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public ActivityFeedResponse handle(GetActivityFeedCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        int page = command.page();
        int size = command.size();
        String username = command.username();

        List<ActivityEventEntity> events;
        long totalCount;

        if (username != null && !username.isBlank()) {
            events = activityEventRepository
                    .findByLeagueIdAndUsernameOrderByCreatedAtDesc(command.leagueId(), username, page, size);
            totalCount = activityEventRepository.countByLeagueIdAndUsername(command.leagueId(), username);
        } else {
            events = activityEventRepository
                    .findByLeagueIdOrderByCreatedAtDesc(command.leagueId(), page, size);
            totalCount = activityEventRepository.countByLeagueId(command.leagueId());
        }

        int totalPages = size > 0 ? (int) Math.ceil((double) totalCount / size) : 0;
        boolean hasMore = (long) (page + 1) * size < totalCount;

        List<ActivityEventResponse> responses = events.stream()
                .map(this::toResponse)
                .toList();

        return ActivityFeedResponse.builder()
                .events(responses)
                .page(page)
                .totalPages(totalPages)
                .hasMore(hasMore)
                .build();
    }

    private ActivityEventResponse toResponse(ActivityEventEntity entity) {
        return ActivityEventResponse.builder()
                .id(entity.getId())
                .leagueId(entity.getLeagueId())
                .type(entity.getType())
                .actorUsername(entity.getActorUsername())
                .targetUsername(entity.getTargetUsername())
                .pokemonName(entity.getPokemonName())
                .pokemonName2(entity.getPokemonName2())
                .coinsAmount(entity.getCoinsAmount())
                .fromTier(entity.getFromTier())
                .toTier(entity.getToTier())
                .roundNumber(entity.getRoundNumber())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Override
    public Class<GetActivityFeedCommand> commandType() {
        return GetActivityFeedCommand.class;
    }
}
