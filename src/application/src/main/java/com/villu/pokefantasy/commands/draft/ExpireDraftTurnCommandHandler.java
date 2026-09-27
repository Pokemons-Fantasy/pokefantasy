package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class ExpireDraftTurnCommandHandler implements CommandHandler<ExpireDraftTurnCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final DraftTurnTimeoutService draftTurnTimeoutService;

    public ExpireDraftTurnCommandHandler(LeagueRepository leagueRepository,
                                         DraftTurnTimeoutService draftTurnTimeoutService) {
        this.leagueRepository = leagueRepository;
        this.draftTurnTimeoutService = draftTurnTimeoutService;
    }

    @Override
    public Void handle(ExpireDraftTurnCommand command) throws Exception {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));
        // Vuelve a comprobar el vencimiento dentro de la transacción: entre el listado y aquí el jugador
        // o un cliente pueden haber hecho ya el pick.
        draftTurnTimeoutService.autoPickExpiredTurn(league);
        return null;
    }

    @Override
    public Class<ExpireDraftTurnCommand> commandType() {
        return ExpireDraftTurnCommand.class;
    }
}
