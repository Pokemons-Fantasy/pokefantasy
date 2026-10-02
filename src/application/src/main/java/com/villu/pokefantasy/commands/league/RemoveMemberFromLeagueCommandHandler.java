package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.commands.draft.DraftCompletionService;
import com.villu.pokefantasy.commands.draft.DraftTurnService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RemoveMemberFromLeagueCommandHandler implements CommandHandler<RemoveMemberFromLeagueCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final DraftTurnService draftTurnService;
    private final DraftCompletionService draftCompletionService;

    public RemoveMemberFromLeagueCommandHandler(LeagueRepository leagueRepository,
                                                DraftRepository draftRepository,
                                                ClosedListRepository closedListRepository,
                                                DraftTurnService draftTurnService,
                                                DraftCompletionService draftCompletionService) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.draftTurnService = draftTurnService;
        this.draftCompletionService = draftCompletionService;
    }

    @Override
    public Void handle(RemoveMemberFromLeagueCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        boolean isSelfLeave = command.requestingUsername().equals(command.targetUsername());
        boolean requesterIsAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.requestingUsername()) && m.getLeagueRole() == LeagueRole.ADMIN);

        if (!isSelfLeave && !requesterIsAdmin) {
            throw new ForbiddenOperationException("Solo un admin puede expulsar a otros miembros");
        }

        boolean targetExists = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()));
        if (!targetExists) {
            throw new IllegalArgumentException("'" + command.targetUsername() + "' no es miembro de esta liga");
        }

        boolean targetIsAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()) && m.getLeagueRole() == LeagueRole.ADMIN);
        if (targetIsAdmin) {
            long adminCount = league.getMembers().stream().filter(m -> m.getLeagueRole() == LeagueRole.ADMIN).count();
            if (adminCount <= 1) {
                throw new IllegalStateException("La liga no puede quedarse sin admin");
            }
        }

        leagueRepository.removeMember(command.leagueId(), command.targetUsername());

        draftRepository.findActiveByLeagueId(command.leagueId()).ifPresent(draft -> {
            // Sus Pokémon vuelven al pool antes de decidir a quién le toca.
            draft.getPicks().removeIf(pick -> pick.getUsername().equals(command.targetUsername()));
            List<ClosedListEntity> available = draft.getConfig() == null ? List.of()
                    : draftTurnService.available(draft, closedListRepository.findAllByLeagueId(command.leagueId()));
            draftTurnService.removePlayer(draft, command.targetUsername(), available,
                    draftTurnService.maxTeamSize(league));
            draftRepository.save(draft);
            if (draft.getStatus() == DraftStatus.COMPLETED) {
                // La liga se relee: la entidad cargada arriba aún tiene al expulsado entre los miembros.
                draftCompletionService.complete(command.leagueId(),
                        leagueRepository.findById(command.leagueId()).orElse(null), draft);
            }
        });

        return null;
    }

    @Override
    public Class<RemoveMemberFromLeagueCommand> commandType() {
        return RemoveMemberFromLeagueCommand.class;
    }
}
