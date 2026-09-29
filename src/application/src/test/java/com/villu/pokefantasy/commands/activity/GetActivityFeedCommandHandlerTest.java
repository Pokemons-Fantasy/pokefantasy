package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.ActivityEventFilter;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.response.ActivityFeedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetActivityFeedCommandHandlerTest {

    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private LeagueMembershipGuard leagueMembershipGuard;

    private GetActivityFeedCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";

    @BeforeEach
    void setUp() {
        handler = new GetActivityFeedCommandHandler(activityEventRepository, leagueMembershipGuard);
    }

    @Test
    void handle_emptyFeed_returnsEmptyPage() {
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 20))
                .thenReturn(List.of());
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(0L);

        ActivityFeedResponse result = handler.handle(new GetActivityFeedCommand(LEAGUE_ID, null, null, 0, 20, "ash"));

        assertThat(result.getEvents()).isEmpty();
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(0);
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void handle_singlePageExact_hasMoreFalse() {
        List<ActivityEventEntity> events = List.of(
                event("e1", ActivityEventType.STEAL, "ash", "brock", "charizard")
        );
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 20))
                .thenReturn(events);
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(1L);

        ActivityFeedResponse result = handler.handle(new GetActivityFeedCommand(LEAGUE_ID, null, null, 0, 20, "ash"));

        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void handle_multiplePages_hasMoreTrueForFirstPage() {
        List<ActivityEventEntity> page0Events = List.of(
                event("e1", ActivityEventType.STEAL, "ash", "brock", "charizard"),
                event("e2", ActivityEventType.BENCH_SWAP, "misty", null, "pikachu")
        );
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 2))
                .thenReturn(page0Events);
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(5L);

        ActivityFeedResponse result = handler.handle(new GetActivityFeedCommand(LEAGUE_ID, null, null, 0, 2, "ash"));

        assertThat(result.getEvents()).hasSize(2);
        assertThat(result.getTotalPages()).isEqualTo(3); // ceil(5/2)
        assertThat(result.isHasMore()).isTrue();
    }

    @Test
    void handle_lastPage_hasMoreFalse() {
        List<ActivityEventEntity> page2Events = List.of(
                event("e5", ActivityEventType.MATCH_RESULT, "ash", "brock", null)
        );
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 2, 2))
                .thenReturn(page2Events);
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(5L);

        ActivityFeedResponse result = handler.handle(new GetActivityFeedCommand(LEAGUE_ID, null, null, 2, 2, "ash"));

        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void handle_mapsAllFieldsCorrectly() {
        Instant createdAt = Instant.parse("2026-05-20T10:00:00Z");
        ActivityEventEntity entity = ActivityEventEntity.builder()
                .id("ev-1")
                .leagueId(LEAGUE_ID)
                .type(ActivityEventType.STEAL)
                .actorUsername("ash")
                .targetUsername("brock")
                .pokemonName("charizard")
                .pokemonName2(null)
                .coinsAmount(300)
                .targetCoinsAmount(300)
                .fromTier(null)
                .toTier(null)
                .roundNumber(null)
                .createdAt(createdAt)
                .build();

        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 20))
                .thenReturn(List.of(entity));
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(1L);

        ActivityFeedResponse result = handler.handle(new GetActivityFeedCommand(LEAGUE_ID, null, null, 0, 20, "ash"));

        assertThat(result.getEvents()).hasSize(1);
        var response = result.getEvents().get(0);
        assertThat(response.getId()).isEqualTo("ev-1");
        assertThat(response.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(response.getType()).isEqualTo(ActivityEventType.STEAL);
        assertThat(response.getActorUsername()).isEqualTo("ash");
        assertThat(response.getTargetUsername()).isEqualTo("brock");
        assertThat(response.getPokemonName()).isEqualTo("charizard");
        assertThat(response.getCoinsAmount()).isEqualTo(300);
        assertThat(response.getTargetCoinsAmount()).isEqualTo(300);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void handle_withUsername_filtersByUser() {
        List<ActivityEventEntity> userEvents = List.of(
                event("e1", ActivityEventType.STEAL, "ash", "brock", "charizard"),
                event("e2", ActivityEventType.COIN_EARNED, "ash", null, null)
        );
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, "ash", null), 0, 20))
                .thenReturn(userEvents);
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, "ash", null))).thenReturn(2L);

        ActivityFeedResponse result = handler.handle(new GetActivityFeedCommand(LEAGUE_ID, "ash", null, 0, 20, "ash"));

        assertThat(result.getEvents()).hasSize(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void handle_sizeAboveCap_isClampedTo100() {
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 100))
                .thenReturn(List.of());
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(0L);

        handler.handle(new GetActivityFeedCommand(LEAGUE_ID, null, null, 0, 500, "ash"));

        verify(activityEventRepository).find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 100);
    }

    @Test
    void handle_withTypes_filtersByTypes() {
        Set<ActivityEventType> types = Set.of(ActivityEventType.STEAL, ActivityEventType.TRADE_COMPLETED);
        ActivityEventFilter filter = new ActivityEventFilter(LEAGUE_ID, "ash", types);
        when(activityEventRepository.find(filter, 0, 20)).thenReturn(List.of());
        when(activityEventRepository.count(filter)).thenReturn(0L);

        handler.handle(new GetActivityFeedCommand(LEAGUE_ID, "ash", types, 0, 20, "ash"));

        verify(activityEventRepository).find(filter, 0, 20);
    }

    @Test
    void handle_blankUsernameAndNoTypes_meanWholeLeague() {
        when(activityEventRepository.find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 20)).thenReturn(List.of());
        when(activityEventRepository.count(new ActivityEventFilter(LEAGUE_ID, null, null))).thenReturn(0L);

        handler.handle(new GetActivityFeedCommand(LEAGUE_ID, " ", Set.of(), 0, 20, "ash"));

        verify(activityEventRepository).find(new ActivityEventFilter(LEAGUE_ID, null, null), 0, 20);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetActivityFeedCommand.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ActivityEventEntity event(String id, ActivityEventType type, String actor,
                                      String target, String pokemonName) {
        return ActivityEventEntity.builder()
                .id(id)
                .leagueId(LEAGUE_ID)
                .type(type)
                .actorUsername(actor)
                .targetUsername(target)
                .pokemonName(pokemonName)
                .createdAt(Instant.now())
                .build();
    }
}
