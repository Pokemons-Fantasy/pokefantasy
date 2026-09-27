package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.draft.DraftFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftTurnTimeoutJobTest {

    @Mock private DraftFacade draftFacade;
    @Mock private RealtimeNotifier realtimeNotifier;

    private DraftTurnTimeoutJob job;

    @BeforeEach
    void setUp() {
        job = new DraftTurnTimeoutJob(draftFacade, realtimeNotifier);
    }

    @Test
    void autoPicksEveryExpiredLeagueAndNotifiesClients() throws Exception {
        when(draftFacade.expiredTurnLeagueIds()).thenReturn(List.of("l1", "l2"));

        job.autoPickExpiredTurns();

        verify(draftFacade).expireTurn("l1");
        verify(draftFacade).expireTurn("l2");
        verify(realtimeNotifier).draftUpdated("l1");
        verify(realtimeNotifier).draftUpdated("l2");
    }

    @Test
    void oneLeagueFailing_doesNotStopTheOthers() throws Exception {
        when(draftFacade.expiredTurnLeagueIds()).thenReturn(List.of("raced", "broken", "ok"));
        doThrow(new IllegalStateException("Turn timer has not expired yet")).when(draftFacade).expireTurn("raced");
        doThrow(new RuntimeException("mongo down")).when(draftFacade).expireTurn("broken");

        job.autoPickExpiredTurns();

        verify(draftFacade).expireTurn("ok");
        verify(realtimeNotifier).draftUpdated("ok");
        verify(realtimeNotifier, never()).draftUpdated("raced");
        verify(realtimeNotifier, never()).draftUpdated("broken");
    }

    @Test
    void listingFails_doesNothing() throws Exception {
        when(draftFacade.expiredTurnLeagueIds()).thenThrow(new RuntimeException("mongo down"));

        job.autoPickExpiredTurns();

        verify(draftFacade, never()).expireTurn(anyString());
        verifyNoInteractions(realtimeNotifier);
    }
}
