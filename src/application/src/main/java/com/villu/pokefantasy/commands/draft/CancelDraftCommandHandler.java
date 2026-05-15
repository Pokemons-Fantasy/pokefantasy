package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

@Service
public class CancelDraftCommandHandler implements CommandHandler<CancelDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public CancelDraftCommandHandler(DraftRepository draftRepository, LeagueAdminGuard leagueAdminGuard) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public Void handle(CancelDraftCommand command) {
        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        DraftEntity draft = draftRepository.findActiveByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException("No active draft found for league: " + command.leagueId()));

        draft.setStatus(DraftStatus.CANCELLED);
        draftRepository.save(draft);
        return null;
    }

    @Override
    public Class<CancelDraftCommand> commandType() {
        return CancelDraftCommand.class;
    }
}
