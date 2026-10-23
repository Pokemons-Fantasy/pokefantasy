package com.villu.pokefantasy.league;

import com.villu.pokefantasy.commands.draft.DraftCompletionService;
import com.villu.pokefantasy.commands.draft.DraftTurnService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Saca a un miembro de una liga: lo quita de la liga y del draft activo (sus Pokémon vuelven a la banca) y
 * recalcula los turnos. Dueño único para la expulsión y el borrado de cuenta. Los permisos los comprueba
 * quien lo llama.
 */
@Service
public class LeagueMemberRemoval {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final DraftTurnService draftTurnService;
    private final DraftCompletionService draftCompletionService;

    public LeagueMemberRemoval(LeagueRepository leagueRepository, DraftRepository draftRepository,
                               ClosedListRepository closedListRepository, DraftTurnService draftTurnService,
                               DraftCompletionService draftCompletionService) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.draftTurnService = draftTurnService;
        this.draftCompletionService = draftCompletionService;
    }

    public void remove(LeagueEntity league, String username) {
        String leagueId = league.getId();
        leagueRepository.removeMember(leagueId, username);

        draftRepository.findActiveByLeagueId(leagueId).ifPresentOrElse(draft -> {
            // Sus Pokémon vuelven al pool antes de decidir a quién le toca.
            draft.getPicks().removeIf(pick -> pick.getUsername().equals(username));
            List<ClosedListEntity> available = draft.getConfig() == null ? List.of()
                    : draftTurnService.available(draft, closedListRepository.findAllByLeagueId(leagueId));
            draftTurnService.removePlayer(draft, username, available, draftTurnService.maxTeamSize(league));
            draftRepository.save(draft);
            if (draft.getStatus() == DraftStatus.COMPLETED) {
                // La liga se relee: la entidad recibida aún tiene al miembro que sale.
                draftCompletionService.complete(leagueId, leagueRepository.findById(leagueId).orElse(null), draft);
            }
        }, () -> returnPicksOfCompletedDraft(leagueId, username));
    }

    /** Temporada en marcha (draft ya cerrado): sus Pokémon no pueden seguir siendo de quien se va. */
    private void returnPicksOfCompletedDraft(String leagueId, String username) {
        draftRepository.findLatestByLeagueId(leagueId)
                .filter(draft -> draft.getStatus() == DraftStatus.COMPLETED)
                .filter(draft -> draft.getPicks().removeIf(pick -> pick.getUsername().equals(username)))
                .ifPresent(draftRepository::save);
    }
}
