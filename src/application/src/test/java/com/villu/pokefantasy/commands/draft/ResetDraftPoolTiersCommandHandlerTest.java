package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetDraftPoolTiersCommandHandlerTest {

    @Mock private DraftSetupGuard draftSetupGuard;
    @Mock private TierAssignmentService tierAssignmentService;

    @Test
    void handle_usesLeaguePercentages() {
        LeagueEntity league = new LeagueEntity();
        league.setSettings(LeagueSettings.builder().tierPctS(40).build());
        when(draftSetupGuard.requireDraftInSetup("l1", "ash"))
                .thenReturn(new DraftSetupGuard.DraftSetup(league, new DraftEntity()));

        new ResetDraftPoolTiersCommandHandler(draftSetupGuard, tierAssignmentService)
                .handle(new ResetDraftPoolTiersCommand("l1", "ash"));

        verify(tierAssignmentService).assignTiersToPool(eq("l1"), argThat(s -> s.getTierPctS() == 40));
    }

    @Test
    void handle_leagueWithoutSettings_usesDefaults() {
        when(draftSetupGuard.requireDraftInSetup("l1", "ash"))
                .thenReturn(new DraftSetupGuard.DraftSetup(new LeagueEntity(), new DraftEntity()));

        new ResetDraftPoolTiersCommandHandler(draftSetupGuard, tierAssignmentService)
                .handle(new ResetDraftPoolTiersCommand("l1", "ash"));

        verify(tierAssignmentService).assignTiersToPool(eq("l1"), argThat(s -> s.getTierPctS() == 20));
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(new ResetDraftPoolTiersCommandHandler(draftSetupGuard, tierAssignmentService).commandType())
                .isEqualTo(ResetDraftPoolTiersCommand.class);
    }
}
