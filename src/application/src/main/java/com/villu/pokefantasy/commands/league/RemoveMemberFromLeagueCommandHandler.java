package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class RemoveMemberFromLeagueCommandHandler implements CommandHandler<RemoveMemberFromLeagueCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;

    public RemoveMemberFromLeagueCommandHandler(LeagueRepository leagueRepository, DraftRepository draftRepository) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    public Void handle(RemoveMemberFromLeagueCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        boolean isSelfLeave = command.requestingUsername().equals(command.targetUsername());
        boolean requesterIsAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.requestingUsername()) && m.getLeagueRole() == LeagueRole.ADMIN);

        if (!isSelfLeave && !requesterIsAdmin) {
            throw new ForbiddenOperationException("Only admins can remove other members");
        }

        boolean targetExists = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()));
        if (!targetExists) {
            throw new IllegalArgumentException("User '" + command.targetUsername() + "' is not a member of this league");
        }

        boolean targetIsAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.targetUsername()) && m.getLeagueRole() == LeagueRole.ADMIN);
        if (targetIsAdmin) {
            long adminCount = league.getMembers().stream().filter(m -> m.getLeagueRole() == LeagueRole.ADMIN).count();
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot remove the last admin of the league");
            }
        }

        leagueRepository.removeMember(command.leagueId(), command.targetUsername());

        draftRepository.findActiveByLeagueId(command.leagueId()).ifPresent(draft -> {
            removePlayerFromDraft(draft, command.targetUsername());
            draftRepository.save(draft);
        });

        return null;
    }

    private void removePlayerFromDraft(DraftEntity draft, String username) {
        draft.getPicks().removeIf(pick -> pick.getUsername().equals(username));

        int removedIndex = draft.getTurnOrder().indexOf(username);
        if (removedIndex == -1) return;

        draft.getTurnOrder().remove(removedIndex);

        if (draft.getTurnOrder().isEmpty()) return;

        int currentIndex = draft.getCurrentTurnIndex();
        if (removedIndex < currentIndex) {
            draft.setCurrentTurnIndex(currentIndex - 1);
        } else if (removedIndex == currentIndex) {
            draft.setCurrentTurnIndex(currentIndex % draft.getTurnOrder().size());
        }
    }

    @Override
    public Class<RemoveMemberFromLeagueCommand> commandType() {
        return RemoveMemberFromLeagueCommand.class;
    }
}
