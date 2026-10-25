package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import com.villu.pokefantasy.commands.schedule.replay.AutoImportTarget;
import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.ports.ShowdownReplay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayAutoImportJobTest {

    @Mock private ScheduleFacade scheduleFacade;

    private ReplayAutoImportJob job;

    private static final AutoImportTarget RACED = new AutoImportTarget("l1", "raced");
    private static final AutoImportTarget DOWN = new AutoImportTarget("l1", "down");
    private static final AutoImportTarget EMPTY = new AutoImportTarget("l1", "empty");
    private static final AutoImportTarget OK = new AutoImportTarget("l2", "ok");

    @BeforeEach
    void setUp() {
        job = new ReplayAutoImportJob(scheduleFacade);
    }

    @Test
    void importsEveryTargetWithASeries_evenIfOthersFail() throws Exception {
        List<ShowdownReplay> series = List.of(new ShowdownReplay("g1", "gen9doublescustomgame", List.of(), "", 1L));
        when(scheduleFacade.autoImportTargets()).thenReturn(List.of(RACED, DOWN, EMPTY, OK));
        // Importado a mano mientras tanto: el comando relee y ve el partido completado.
        when(scheduleFacade.pickAutoImport(RACED)).thenReturn(Optional.of(series));
        org.mockito.Mockito.doThrow(new IllegalStateException("El resultado de este partido ya se ha registrado."))
                .when(scheduleFacade).importSeriesAsSystem(RACED, series);
        when(scheduleFacade.pickAutoImport(DOWN)).thenThrow(new ShowdownUnavailableException("down", null));
        when(scheduleFacade.pickAutoImport(EMPTY)).thenReturn(Optional.empty());
        when(scheduleFacade.pickAutoImport(OK)).thenReturn(Optional.of(series));

        job.importReplays();

        verify(scheduleFacade).importSeriesAsSystem(OK, series);
        verify(scheduleFacade, never()).importSeriesAsSystem(org.mockito.ArgumentMatchers.eq(EMPTY), any());
    }

    @Test
    void listingFails_doesNothing() throws Exception {
        when(scheduleFacade.autoImportTargets()).thenThrow(new RuntimeException("mongo down"));

        job.importReplays();

        verify(scheduleFacade, never()).pickAutoImport(any());
    }
}
