package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class StartDraftCommandHandler implements CommandHandler<StartDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public StartDraftCommandHandler(DraftRepository draftRepository,
                                    LeagueAdminGuard leagueAdminGuard) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public Void handle(StartDraftCommand command) {
        if (command == null || command.turnOrder() == null || command.turnOrder().isEmpty()) {
            throw new IllegalArgumentException("Turn order must have at least one player");
        }

        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        List<String> sanitizedTurnOrder = new ArrayList<>();
        Set<String> seenUsers = new HashSet<>();
        for (String username : command.turnOrder()) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Turn order cannot contain blank usernames");
            }

            String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
            if (!seenUsers.add(normalizedUsername)) {
                throw new IllegalArgumentException("Turn order cannot contain duplicate usernames");
            }

            sanitizedTurnOrder.add(username.trim());
        }

        draftRepository.findActiveByLeagueId(command.leagueId()).ifPresent(d -> {
            throw new IllegalStateException("A draft is already active with status: " + d.getStatus());
        });

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(sanitizedTurnOrder);
        draft.setCurrentTurnIndex(0);
        draft.setCurrentRound(1);
        draft.setPicks(new ArrayList<>());
        draft.setLeagueId(command.leagueId());

        draftRepository.save(draft);
        return null;
    }

    @Override
    public Class<StartDraftCommand> commandType() {
        return StartDraftCommand.class;
    }
}
