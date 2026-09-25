package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ListExpiredDraftTurnsCommandHandler implements CommandHandler<ListExpiredDraftTurnsCommand, List<String>> {

    private final DraftRepository draftRepository;
    private final LeagueRepository leagueRepository;

    public ListExpiredDraftTurnsCommandHandler(DraftRepository draftRepository, LeagueRepository leagueRepository) {
        this.draftRepository = draftRepository;
        this.leagueRepository = leagueRepository;
    }

    @Override
    public List<String> handle(ListExpiredDraftTurnsCommand command) {
        Instant now = Instant.now();
        List<String> expired = new ArrayList<>();
        for (DraftEntity draft : draftRepository.findAllInProgress()) {
            leagueRepository.findById(draft.getLeagueId())
                    .flatMap(league -> DraftTurnTimeoutService.turnDeadline(draft, league))
                    .filter(deadline -> !now.isBefore(deadline))
                    .ifPresent(deadline -> expired.add(draft.getLeagueId()));
        }
        return expired;
    }

    @Override
    public Class<ListExpiredDraftTurnsCommand> commandType() {
        return ListExpiredDraftTurnsCommand.class;
    }
}
