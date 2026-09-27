package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.exception.StaleOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.team.TeamOperation;
import com.villu.pokefantasy.team.TeamTransferService;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RespondToTradeCommandHandler implements CommandHandler<RespondToTradeCommand, Void> {

    private final TradeRepository tradeRepository;
    private final DraftRepository draftRepository;
    private final LeagueRepository leagueRepository;
    private final TeamTransferService teamTransferService;
    private final ActivityEventRepository activityEventRepository;

    public RespondToTradeCommandHandler(TradeRepository tradeRepository,
                                        DraftRepository draftRepository,
                                        LeagueRepository leagueRepository,
                                        TeamTransferService teamTransferService,
                                        ActivityEventRepository activityEventRepository) {
        this.tradeRepository = tradeRepository;
        this.draftRepository = draftRepository;
        this.leagueRepository = leagueRepository;
        this.teamTransferService = teamTransferService;
        this.activityEventRepository = activityEventRepository;
    }

    @Override
    public Void handle(RespondToTradeCommand command) {
        TradeEntity trade = tradeRepository.findById(command.tradeId())
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + command.tradeId()));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new IllegalStateException("Esta propuesta ya no está pendiente");
        }
        if (!trade.getResponder().equalsIgnoreCase(command.respondingUser())) {
            throw new ForbiddenOperationException("Solo el destinatario puede responder a esta propuesta");
        }

        if (!command.accept()) {
            trade.setStatus(TradeStatus.REJECTED);
            trade.setResolvedAt(Instant.now());
            tradeRepository.save(trade);
            return null;
        }

        executeTrade(trade);
        return null;
    }

    private void executeTrade(TradeEntity trade) {
        String leagueId = trade.getLeagueId();

        TeamTransferService.Market market = teamTransferService.openMarket(leagueId, TeamOperation.TRADE);
        DraftEntity draft = market.draft();
        LeagueEntity league = market.league();

        DraftPick proposerPick = findPickOrInvalidate(draft, trade,
                trade.getProposer(), trade.getProposerPokemonName());
        DraftPick responderPick = findPickOrInvalidate(draft, trade,
                trade.getResponder(), trade.getResponderPokemonName());

        teamTransferService.requireUnlocked(proposerPick);
        teamTransferService.requireUnlocked(responderPick);

        LeagueMember proposerMember = getMember(league, trade.getProposer());
        LeagueMember responderMember = getMember(league, trade.getResponder());
        if (proposerMember.getCoinBalance() < trade.getCoinsOffered()) {
            throw new IllegalStateException(
                    "El proponente ya no tiene suficientes monedas para esta oferta.");
        }

        // 1. Monedas A -> B
        proposerMember.setCoinBalance(proposerMember.getCoinBalance() - trade.getCoinsOffered());
        responderMember.setCoinBalance(responderMember.getCoinBalance() + trade.getCoinsOffered());
        leagueRepository.save(league);

        // 2. DraftPicks: intercambio de dueño + bloqueo
        Instant now = Instant.now();
        teamTransferService.transfer(proposerPick, trade.getResponder(), now);
        teamTransferService.transfer(responderPick, trade.getProposer(), now);

        draftRepository.save(draft);

        // 3. Trade aceptado
        trade.setStatus(TradeStatus.ACCEPTED);
        trade.setResolvedAt(now);
        tradeRepository.save(trade);

        // 4. Auto-cancelar propuestas en conflicto
        cancelConflicting(leagueId, trade.getId(),
                trade.getProposerPokemonName(), trade.getResponderPokemonName());

        // 5. Activity event
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.TRADE_COMPLETED)
                .actorUsername(trade.getProposer())
                .targetUsername(trade.getResponder())
                .pokemonName(trade.getProposerPokemonName())
                .pokemonName2(trade.getResponderPokemonName())
                .coinsAmount(trade.getCoinsOffered() > 0 ? trade.getCoinsOffered() : null)
                .createdAt(now)
                .build());
    }

    private DraftPick findPickOrInvalidate(DraftEntity draft, TradeEntity trade,
                                           String username, String pokemonName) {
        return draft.getPicks().stream()
                .filter(p -> username.equalsIgnoreCase(p.getUsername())
                        && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseGet(() -> {
                    trade.setStatus(TradeStatus.CANCELLED);
                    trade.setResolvedAt(Instant.now());
                    tradeRepository.save(trade);
                    // StaleOperationException confirma la transacción: la cancelación del trade se persiste.
                    throw new StaleOperationException("La propuesta ya no es válida: '"
                            + pokemonName + "' ha cambiado de dueño.");
                });
    }

    private void cancelConflicting(String leagueId, String executedTradeId,
                                   String name1, String name2) {
        for (TradeEntity other : tradeRepository.findPendingByLeagueId(leagueId)) {
            if (other.getId().equals(executedTradeId)) continue;
            boolean conflicts = matches(other.getProposerPokemonName(), name1, name2)
                    || matches(other.getResponderPokemonName(), name1, name2);
            if (conflicts) {
                other.setStatus(TradeStatus.CANCELLED);
                other.setResolvedAt(Instant.now());
                tradeRepository.save(other);
            }
        }
    }

    private boolean matches(String name, String a, String b) {
        return name.equalsIgnoreCase(a) || name.equalsIgnoreCase(b);
    }

    private LeagueMember getMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equalsIgnoreCase(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Member not found: " + username));
    }

    @Override
    public Class<RespondToTradeCommand> commandType() {
        return RespondToTradeCommand.class;
    }
}
