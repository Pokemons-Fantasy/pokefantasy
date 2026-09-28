package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StartDraftCommandHandler implements CommandHandler<StartDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final LeagueRepository leagueRepository;
    private final TierAssignmentService tierAssignmentService;
    private final DraftTurnNotifier draftTurnNotifier;

    public StartDraftCommandHandler(DraftRepository draftRepository,
                                    LeagueAdminGuard leagueAdminGuard,
                                    LeagueRepository leagueRepository,
                                    TierAssignmentService tierAssignmentService,
                                    DraftTurnNotifier draftTurnNotifier) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.leagueRepository = leagueRepository;
        this.tierAssignmentService = tierAssignmentService;
        this.draftTurnNotifier = draftTurnNotifier;
    }

    @Override
    public Void handle(StartDraftCommand command) {
        if (command == null || command.turnOrder() == null || command.turnOrder().isEmpty()) {
            throw new IllegalArgumentException("El orden de turnos debe tener al menos un jugador");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        List<String> sanitizedTurnOrder = new ArrayList<>();
        Set<String> seenUsers = new HashSet<>();
        for (String username : command.turnOrder()) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("El orden de turnos tiene un jugador sin nombre");
            }

            String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
            if (!seenUsers.add(normalizedUsername)) {
                throw new IllegalArgumentException("El orden de turnos tiene jugadores repetidos");
            }

            sanitizedTurnOrder.add(username.trim());
        }

        draftRepository.findActiveByLeagueId(command.leagueId()).ifPresent(d -> {
            throw new IllegalStateException("Ya hay un draft en marcha en esta liga");
        });

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(matchLeagueMembers(sanitizedTurnOrder, league));
        draft.setCurrentTurnIndex(0);
        draft.setCurrentRound(1);
        draft.setPicks(new ArrayList<>());
        draft.setLeagueId(command.leagueId());
        draft.setCurrentTurnStartedAt(Instant.now());

        draftRepository.save(draft);
        assignTiersToPool(command.leagueId());
        draftTurnNotifier.notifyCurrentTurn(draft, leagueRepository.findById(command.leagueId()).orElse(null));
        return null;
    }

    /**
     * El orden de turnos tiene que ser exactamente la lista de miembros: si falta alguien, se queda sin equipo;
     * si sobra alguien, recibe picks sin estar en la liga. Devuelve los nombres tal como están en la liga.
     */
    private List<String> matchLeagueMembers(List<String> turnOrder, LeagueEntity league) {
        Map<String, String> membersByKey = new LinkedHashMap<>();
        if (league.getMembers() != null) {
            league.getMembers().forEach(m -> membersByKey.put(m.getUsername().toLowerCase(Locale.ROOT), m.getUsername()));
        }

        List<String> canonical = new ArrayList<>();
        List<String> strangers = new ArrayList<>();
        for (String username : turnOrder) {
            String member = membersByKey.remove(username.toLowerCase(Locale.ROOT));
            if (member == null) {
                strangers.add(username);
            } else {
                canonical.add(member);
            }
        }

        if (!strangers.isEmpty()) {
            throw new IllegalArgumentException("No son miembros de la liga: " + String.join(", ", strangers));
        }
        if (!membersByKey.isEmpty()) {
            throw new IllegalArgumentException("Faltan en el orden de turnos: " + String.join(", ", membersByKey.values()));
        }
        return canonical;
    }

    private void assignTiersToPool(String leagueId) {
        LeagueSettings settings = leagueRepository.findById(leagueId)
                .map(l -> l.getSettings() != null ? l.getSettings() : LeagueSettings.defaults())
                .orElse(LeagueSettings.defaults());
        tierAssignmentService.assignTiersToPool(leagueId, settings);
    }

    @Override
    public Class<StartDraftCommand> commandType() {
        return StartDraftCommand.class;
    }
}
