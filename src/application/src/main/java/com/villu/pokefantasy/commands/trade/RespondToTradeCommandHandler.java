package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class RespondToTradeCommandHandler implements CommandHandler<RespondToTradeCommand, Void> {

    private final TradeRepository tradeRepository;
    private final DraftRepository draftRepository;
    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ClosedListRepository closedListRepository;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;

    public RespondToTradeCommandHandler(TradeRepository tradeRepository,
                                        DraftRepository draftRepository,
                                        ScheduleRepository scheduleRepository,
                                        LeagueRepository leagueRepository,
                                        UserRepository userRepository,
                                        ClosedListRepository closedListRepository,
                                        JornadaWindowService jornadaWindowService,
                                        ActivityEventRepository activityEventRepository) {
        this.tradeRepository = tradeRepository;
        this.draftRepository = draftRepository;
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.closedListRepository = closedListRepository;
        this.jornadaWindowService = jornadaWindowService;
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

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(
                        "Trades are only allowed after the draft is completed"));

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));

        if (!jornadaWindowService.isSwapWindowOpen(schedule)) {
            throw new IllegalStateException(
                    "La ventana de intercambios no está abierta. El plazo cerró el viernes a las 16:00.");
        }

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        DraftPick proposerPick = findPickOrInvalidate(draft, trade,
                trade.getProposer(), trade.getProposerPokemonName());
        DraftPick responderPick = findPickOrInvalidate(draft, trade,
                trade.getResponder(), trade.getResponderPokemonName());

        assertNotLocked(proposerPick, trade.getProposerPokemonName());
        assertNotLocked(responderPick, trade.getResponderPokemonName());

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

        // 2. user.getPokemons() de ambos
        movePokemon(trade.getProposer(), trade.getProposerPokemonName(),
                trade.getResponderPokemonName(), trade.getResponderPokemonId(), leagueId);
        movePokemon(trade.getResponder(), trade.getResponderPokemonName(),
                trade.getProposerPokemonName(), trade.getProposerPokemonId(), leagueId);

        // 3. DraftPicks: intercambio de username + bloqueo
        Instant now = Instant.now();
        Instant lockUntil = now.plus(7, ChronoUnit.DAYS);
        proposerPick.setUsername(trade.getResponder());
        proposerPick.setLockedUntil(lockUntil);
        proposerPick.setPickedAt(now);
        responderPick.setUsername(trade.getProposer());
        responderPick.setLockedUntil(lockUntil);
        responderPick.setPickedAt(now);
        draftRepository.save(draft);

        // 4. Trade aceptado
        trade.setStatus(TradeStatus.ACCEPTED);
        trade.setResolvedAt(now);
        tradeRepository.save(trade);

        // 5. Auto-cancelar propuestas en conflicto
        cancelConflicting(leagueId, trade.getId(),
                trade.getProposerPokemonName(), trade.getResponderPokemonName());

        // 6. Activity event
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.TRADE_COMPLETED)
                .actorUsername(trade.getProposer())
                .targetUsername(trade.getResponder())
                .pokemonName(trade.getProposerPokemonName())
                .pokemonName2(trade.getResponderPokemonName())
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
                    throw new IllegalStateException("La propuesta ya no es válida: '"
                            + pokemonName + "' ha cambiado de dueño.");
                });
    }

    private void movePokemon(String username, String giveName,
                             String takeName, int takeId, String leagueId) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) return;
        List<Pokemons> pokemons = user.getPokemons() != null
                ? new ArrayList<>(user.getPokemons()) : new ArrayList<>();
        pokemons.removeIf(p -> leagueId.equals(p.getLeagueId())
                && giveName.equalsIgnoreCase(p.getName()));
        Pokemons received = new Pokemons();
        received.setId(takeId);
        received.setName(takeName);
        received.setLeagueId(leagueId);
        closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(takeName, leagueId)
                .ifPresent(entry -> {
                    received.setStats(entry.getStats());
                    received.setTypes(entry.getTypes());
                });
        pokemons.add(received);
        user.setPokemons(pokemons);
        userRepository.updateUserWithPokemons(user);
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

    private void assertNotLocked(DraftPick pick, String pokemonName) {
        if (pick.getLockedUntil() != null && Instant.now().isBefore(pick.getLockedUntil())) {
            throw new IllegalStateException(
                    "'" + pokemonName + "' está bloqueado hasta " + pick.getLockedUntil() + ".");
        }
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
