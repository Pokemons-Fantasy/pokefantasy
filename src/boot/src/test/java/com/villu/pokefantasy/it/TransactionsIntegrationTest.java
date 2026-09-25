package com.villu.pokefantasy.it;

import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.ActivityEventMongoRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Transacciones de verdad (Mongo en replica set): un comando que falla a mitad no deja escrituras a
 * medias, y comandos concurrentes sobre los mismos documentos no se pisan (@Version + reintentos).
 */
class TransactionsIntegrationTest extends IntegrationTest {

    private static final String LEAGUE = "league-tx";
    private static final String ASH = "ash";
    private static final String BROCK = "brock";
    private static final int MATCHES = 8;

    @Autowired private ScheduleFacade scheduleFacade;

    @MockitoSpyBean private ActivityEventMongoRepository activityEventRepository;

    @BeforeEach
    void seedLeague() {
        reset(activityEventRepository);

        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE);
        league.setName("Liga transacciones");
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember(ASH, LeagueRole.ADMIN, 0),
                new LeagueMember(BROCK, LeagueRole.USER, 0))));
        LeagueSettings settings = LeagueSettings.defaults();
        settings.setCoinsPerWin(100);
        settings.setCoinsPerLoss(50);
        league.setSettings(settings);
        mongoTemplate.insert(league);

        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick(ASH, "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick(BROCK, "onix", 95, 1, Instant.now(), null, null))));
        mongoTemplate.insert(draft);

        List<ScheduleEntity.Match> matches = new ArrayList<>();
        IntStream.range(0, MATCHES).forEach(i ->
                matches.add(new ScheduleEntity.Match("m" + i, ASH, BROCK, null, MatchStatus.PENDING)));
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE);
        schedule.setJornadas(new ArrayList<>(List.of(new ScheduleEntity.Jornada(1, matches, null))));
        mongoTemplate.insert(schedule);
    }

    private int coins(String username) {
        return mongoTemplate.findById(LEAGUE, LeagueEntity.class).getMembers().stream()
                .filter(m -> m.getUsername().equals(username)).findFirst().orElseThrow().getCoinBalance();
    }

    private long completedMatches() {
        return mongoTemplate.findAll(ScheduleEntity.class).getFirst().getJornadas().getFirst().getMatches().stream()
                .filter(m -> m.getStatus() == MatchStatus.COMPLETED).count();
    }

    @Test
    void failureHalfwayThroughACommand_rollsBackEveryWrite() {
        // Recording a result saves the league (coins) and THEN the activity events: we break the latter.
        doThrow(new RuntimeException("disk full")).when(activityEventRepository)
                .save(argThat((ActivityEventEntity e) -> e != null));

        assertThatThrownBy(() -> scheduleFacade.recordResult(LEAGUE, "m0", ASH, ASH))
                .hasMessageContaining("disk full");

        assertThat(coins(ASH)).isZero();
        assertThat(coins(BROCK)).isZero();
        assertThat(completedMatches()).isZero();
        assertThat(mongoTemplate.getCollection("activity_events").countDocuments()).isZero();
    }

    @Test
    void fourConcurrentResults_allApplied_noLostUpdate() throws Exception {
        // Mismo documento de liga y de calendario: chocan, y los reintentos con espera aleatoria los sacan.
        List<Throwable> errors = runConcurrently(4);

        assertThat(errors).isEmpty();
        assertThat(completedMatches()).isEqualTo(4);
        assertThat(coins(ASH)).isEqualTo(400);
        assertThat(coins(BROCK)).isEqualTo(200);
    }

    @Test
    void manyConcurrentResults_coinsAlwaysMatchCompletedMatches() throws Exception {
        List<Throwable> errors = runConcurrently(MATCHES);

        // Con tanta contención sobre el mismo documento alguno puede agotar los reintentos, pero solo con un
        // conflicto limpio (409) y sin dejar rastro: nunca un partido completado sin sus monedas ni monedas
        // sin partido.
        assertThat(errors).allSatisfy(e -> assertThat(e)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("al mismo tiempo"));
        long completed = completedMatches();
        assertThat(completed).isEqualTo(MATCHES - errors.size()).isPositive();
        assertThat(coins(ASH)).isEqualTo(100 * completed);
        assertThat(coins(BROCK)).isEqualTo(50 * completed);
    }

    /** Registra a la vez los resultados m0..m(n-1) y devuelve las excepciones de los que fallaron. */
    private List<Throwable> runConcurrently(int n) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String matchId = "m" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    scheduleFacade.recordResult(LEAGUE, matchId, ASH, ASH);
                    return null;
                }));
            }
            start.countDown();
            List<Throwable> errors = new ArrayList<>();
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    errors.add(e.getCause());
                }
            }
            return errors;
        } finally {
            pool.shutdownNow();
        }
    }
}
