package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** Guarda presupuesto, precios por tier, snake y orden de turnos del draft en preparación. */
@Service
public class UpdateDraftConfigCommandHandler implements CommandHandler<UpdateDraftConfigCommand, Void> {

    private final DraftSetupGuard draftSetupGuard;
    private final DraftRepository draftRepository;
    private final TurnOrderPolicy turnOrderPolicy;

    public UpdateDraftConfigCommandHandler(DraftSetupGuard draftSetupGuard,
                                           DraftRepository draftRepository,
                                           TurnOrderPolicy turnOrderPolicy) {
        this.draftSetupGuard = draftSetupGuard;
        this.draftRepository = draftRepository;
        this.turnOrderPolicy = turnOrderPolicy;
    }

    @Override
    public Void handle(UpdateDraftConfigCommand command) {
        DraftSetupGuard.DraftSetup setup = draftSetupGuard.requireDraftInSetup(command.leagueId(), command.requestingUsername());
        DraftConfig config = command.config();
        if (config == null || config.getBudget() == null || config.getBudget() <= 0) {
            throw new IllegalArgumentException("El presupuesto tiene que ser mayor que 0");
        }
        List<Integer> prices = Arrays.asList(config.getPriceS(), config.getPriceA(), config.getPriceB(),
                config.getPriceC(), config.getPriceD());
        if (prices.contains(null)) {
            throw new IllegalArgumentException("Indica el precio de todos los tiers");
        }
        if (prices.stream().anyMatch(p -> p < 0)) {
            throw new IllegalArgumentException("Los precios de los tiers no pueden ser negativos");
        }

        DraftEntity draft = setup.draft();
        if (command.turnOrder() != null) {
            draft.setTurnOrder(turnOrderPolicy.canonical(command.turnOrder(), setup.league()));
        }
        draft.setConfig(config.toBuilder().snake(Boolean.TRUE.equals(config.getSnake())).build());
        draftRepository.save(draft);
        return null;
    }

    @Override
    public Class<UpdateDraftConfigCommand> commandType() {
        return UpdateDraftConfigCommand.class;
    }
}
