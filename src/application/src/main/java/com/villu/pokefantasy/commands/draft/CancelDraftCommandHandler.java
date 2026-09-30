package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

@Service
public class CancelDraftCommandHandler implements CommandHandler<CancelDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final ClosedListRepository closedListRepository;

    public CancelDraftCommandHandler(DraftRepository draftRepository, LeagueAdminGuard leagueAdminGuard,
                                     ClosedListRepository closedListRepository) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.closedListRepository = closedListRepository;
    }

    @Override
    public Void handle(CancelDraftCommand command) {
        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        DraftEntity draft = draftRepository.findActiveByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException("No hay ningún draft activo en esta liga"));

        if (draft.getStatus() == DraftStatus.PENDING) {
            // Volver a nominaciones: el draft en preparación no tiene picks, se borra y se reabren las nominaciones.
            draftRepository.delete(draft);
        } else {
            draft.setStatus(DraftStatus.CANCELLED);
            draftRepository.save(draft);
        }
        // Se vuelve a nominaciones: los tiers del pool dejan de valer (se recalculan por BST al preparar).
        closedListRepository.clearTiers(command.leagueId());
        return null;
    }

    @Override
    public Class<CancelDraftCommand> commandType() {
        return CancelDraftCommand.class;
    }
}
