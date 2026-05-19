package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StartDraftCommandHandler implements CommandHandler<StartDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final ClosedListRepository closedListRepository;

    public StartDraftCommandHandler(DraftRepository draftRepository,
                                    LeagueAdminGuard leagueAdminGuard,
                                    ClosedListRepository closedListRepository) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.closedListRepository = closedListRepository;
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
        assignTiersToPool(command.leagueId());
        return null;
    }

    private void assignTiersToPool(String leagueId) {
        List<ClosedListEntity> pool = closedListRepository.findAllByLeagueId(leagueId);
        if (pool.isEmpty()) return;

        List<ClosedListEntity> sorted = pool.stream()
                .sorted(Comparator.comparingInt(this::bst).reversed())
                .collect(Collectors.toList());

        int n = sorted.size();
        List<Tier> tiers = List.of(Tier.S, Tier.A, Tier.B, Tier.C, Tier.D);
        for (int i = 0; i < n; i++) {
            int tierIndex = Math.min(i * 5 / n, 4);
            closedListRepository.updateTier(sorted.get(i).getId(), tiers.get(tierIndex));
        }
    }

    private int bst(ClosedListEntity entity) {
        if (entity.getStats() == null) return 0;
        return entity.getStats().stream().mapToInt(Stat::getBaseStat).sum();
    }

    @Override
    public Class<StartDraftCommand> commandType() {
        return StartDraftCommand.class;
    }
}
