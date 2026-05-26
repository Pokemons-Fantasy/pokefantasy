package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AutoPickDraftCommandHandler implements CommandHandler<AutoPickDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final DraftPickCommandHandler draftPickCommandHandler;

    public AutoPickDraftCommandHandler(DraftRepository draftRepository,
                                       ClosedListRepository closedListRepository,
                                       LeagueRepository leagueRepository,
                                       DraftPickCommandHandler draftPickCommandHandler) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.draftPickCommandHandler = draftPickCommandHandler;
    }

    @Override
    public Void handle(AutoPickDraftCommand command) throws Exception {
        DraftEntity draft = draftRepository.findActiveByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException("No active draft found for league: " + command.leagueId()));

        if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
            throw new IllegalStateException("Draft is not in progress");
        }

        LeagueSettings settings = leagueRepository.findById(command.leagueId())
                .map(l -> l.getSettings() != null ? l.getSettings() : LeagueSettings.defaults())
                .orElse(LeagueSettings.defaults());

        Integer timer = settings.getTurnTimerSeconds();
        if (timer == null || timer <= 0) {
            throw new IllegalStateException("Turn timer is not enabled for this league");
        }

        if (draft.getCurrentTurnStartedAt() == null) {
            throw new IllegalStateException("Turn start time not recorded");
        }

        Instant deadline = draft.getCurrentTurnStartedAt().plusSeconds(timer);
        if (Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("Turn timer has not expired yet");
        }

        String currentPlayer = draft.getTurnOrder().get(draft.getCurrentTurnIndex());

        Set<String> pickedNames = draft.getPicks().stream()
                .map(DraftPick::getPokemonName)
                .collect(Collectors.toSet());

        List<ClosedListEntity> available = closedListRepository.findAllByLeagueId(command.leagueId())
                .stream()
                .filter(p -> !pickedNames.contains(p.getPokemonName()))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            throw new IllegalStateException("No Pokémon available for auto-pick");
        }

        String randomPokemon = available.get(new Random().nextInt(available.size())).getPokemonName();

        // Delegate to the standard pick flow (validates, records, advances turn, generates schedule if completed)
        draftPickCommandHandler.handle(new DraftPickCommand(currentPlayer, randomPokemon, command.leagueId()));
        return null;
    }

    @Override
    public Class<AutoPickDraftCommand> commandType() {
        return AutoPickDraftCommand.class;
    }
}
