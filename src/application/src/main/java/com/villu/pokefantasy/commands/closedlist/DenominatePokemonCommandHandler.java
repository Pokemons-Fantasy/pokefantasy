package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import org.springframework.stereotype.Service;

@Service
public class DenominatePokemonCommandHandler implements CommandHandler<DenominatePokemonCommand, Void> {

    private final ClosedListRepository closedListRepository;
    private final DraftRepository draftRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;

    public DenominatePokemonCommandHandler(ClosedListRepository closedListRepository,
                                           DraftRepository draftRepository,
                                           LeagueMembershipGuard leagueMembershipGuard) {
        this.closedListRepository = closedListRepository;
        this.draftRepository = draftRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public Void handle(DenominatePokemonCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.username());

        draftRepository.findLatestByLeagueId(command.leagueId()).ifPresent(draft -> {
            throw new IllegalStateException(draft.getStatus() == DraftStatus.PENDING
                    ? "Ya no se pueden quitar nominaciones: se está preparando el draft"
                    : "Ya no se pueden quitar nominaciones: el draft ya ha empezado");
        });

        closedListRepository.deleteByPokemonNameAndNominatedByAndLeagueId(
                command.pokemonName(), command.username(), command.leagueId());
        return null;
    }

    @Override
    public Class<DenominatePokemonCommand> commandType() {
        return DenominatePokemonCommand.class;
    }
}
