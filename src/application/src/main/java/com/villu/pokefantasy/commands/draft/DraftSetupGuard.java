package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

/** Casos de uso de la preparación del draft: solo el admin y solo con el draft en PENDING. */
@Service
public class DraftSetupGuard {

    public record DraftSetup(LeagueEntity league, DraftEntity draft) {}

    private final LeagueAdminGuard leagueAdminGuard;
    private final DraftRepository draftRepository;

    public DraftSetupGuard(LeagueAdminGuard leagueAdminGuard, DraftRepository draftRepository) {
        this.leagueAdminGuard = leagueAdminGuard;
        this.draftRepository = draftRepository;
    }

    public DraftSetup requireDraftInSetup(String leagueId, String username) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(leagueId, username);
        DraftEntity draft = draftRepository.findActiveByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("El draft no se está preparando"));
        return new DraftSetup(league, draft);
    }
}
