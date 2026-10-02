package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SetStealPriceCommandHandler implements CommandHandler<SetStealPriceCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberService leagueMemberService;
    private final StealClauseService stealClauseService;
    private final ActivityEventRepository activityEventRepository;

    public SetStealPriceCommandHandler(DraftRepository draftRepository,
                                       LeagueRepository leagueRepository,
                                       LeagueMemberService leagueMemberService,
                                       StealClauseService stealClauseService,
                                       ActivityEventRepository activityEventRepository) {
        this.draftRepository = draftRepository;
        this.leagueRepository = leagueRepository;
        this.leagueMemberService = leagueMemberService;
        this.stealClauseService = stealClauseService;
        this.activityEventRepository = activityEventRepository;
    }

    @Override
    public Void handle(SetStealPriceCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonName = command.pokemonName().trim();
        int newPrice = command.newPrice();

        if (newPrice < 0) {
            throw new IllegalArgumentException("El precio de robo no puede ser negativo");
        }

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("No hay draft completado para esta liga"));

        // Find the pick owned by this user
        DraftPick pick = draft.getPicks().stream()
                .filter(p -> username.equals(p.getUsername()) && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + pokemonName + "' no está en tu equipo en esta liga"));

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        int currentClause = stealClauseService.currentClause(league, pick);
        if (newPrice <= currentClause) {
            throw new IllegalArgumentException(
                    "El nuevo precio (" + newPrice + ") debe ser mayor que el precio actual (" + currentClause + ")");
        }

        int investment = stealClauseService.raiseCost(currentClause, newPrice);
        LeagueMember member = leagueMemberService.requireMember(league, username);

        if (member.getCoinBalance() < investment) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas invertir " + investment +
                    " pero tienes " + member.getCoinBalance() + ".");
        }

        member.setCoinBalance(member.getCoinBalance() - investment);
        leagueRepository.save(league);

        int raisedClause = stealClauseService.raisedClause(currentClause, investment);
        pick.setCustomStealPrice(raisedClause);
        draftRepository.save(draft);

        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.CLAUSE_RAISED)
                .actorUsername(username)
                .pokemonName(pick.getPokemonName())
                .coinsAmount(investment)
                .createdAt(Instant.now())
                .build());

        return null;
    }

    @Override
    public Class<SetStealPriceCommand> commandType() {
        return SetStealPriceCommand.class;
    }
}
