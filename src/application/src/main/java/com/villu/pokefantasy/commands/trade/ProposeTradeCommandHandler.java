package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProposeTradeCommandHandler implements CommandHandler<ProposeTradeCommand, Void> {

    private final TradeRepository tradeRepository;
    private final DraftRepository draftRepository;
    private final LeagueRepository leagueRepository;
    private final ScheduleRepository scheduleRepository;

    public ProposeTradeCommandHandler(TradeRepository tradeRepository,
                                      DraftRepository draftRepository,
                                      LeagueRepository leagueRepository,
                                      ScheduleRepository scheduleRepository) {
        this.tradeRepository = tradeRepository;
        this.draftRepository = draftRepository;
        this.leagueRepository = leagueRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public Void handle(ProposeTradeCommand command) {
        String leagueId = command.leagueId();
        String proposer = command.proposer().trim();
        String responder = command.responder().trim();
        String proposerPokemonName = command.proposerPokemonName().trim();
        String responderPokemonName = command.responderPokemonName().trim();
        int coinsOffered = command.coinsOffered();

        if (proposer.equalsIgnoreCase(responder)) {
            throw new IllegalArgumentException("No puedes proponerte un trade a ti mismo");
        }
        if (coinsOffered < 0) {
            throw new IllegalArgumentException("coinsOffered must be >= 0");
        }

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(
                        "Trades are only allowed after the draft is completed"));

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        LeagueMember proposerMember = findMember(league, proposer);
        findMember(league, responder); // valida que el responder es miembro

        DraftPick proposerPick = findPick(draft, proposer, proposerPokemonName);
        DraftPick responderPick = findPick(draft, responder, responderPokemonName);

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId).orElse(null);
        assertNotLocked(proposerPick, schedule, proposerPokemonName);
        assertNotLocked(responderPick, schedule, responderPokemonName);

        if (proposerMember.getCoinBalance() < coinsOffered) {
            throw new IllegalStateException("No tienes suficientes monedas para esta oferta. Necesitas "
                    + coinsOffered + " pero tienes " + proposerMember.getCoinBalance() + ".");
        }

        TradeEntity trade = TradeEntity.builder()
                .leagueId(leagueId)
                .proposer(proposer)
                .responder(responder)
                .proposerPokemonName(proposerPick.getPokemonName())
                .proposerPokemonId(proposerPick.getPokemonId())
                .responderPokemonName(responderPick.getPokemonName())
                .responderPokemonId(responderPick.getPokemonId())
                .coinsOffered(coinsOffered)
                .status(TradeStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        tradeRepository.save(trade);
        return null;
    }

    private LeagueMember findMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equalsIgnoreCase(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(username + " no es miembro de la liga"));
    }

    private DraftPick findPick(DraftEntity draft, String username, String pokemonName) {
        return draft.getPicks().stream()
                .filter(p -> username.equalsIgnoreCase(p.getUsername())
                        && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + pokemonName + "' no está en el equipo de " + username));
    }

    private void assertNotLocked(DraftPick pick, ScheduleEntity schedule, String pokemonName) {
        if (pick.getLockedUntilRound() == null || schedule == null) return;
        int lockedRound = pick.getLockedUntilRound();
        boolean stillLocked = schedule.getJornadas().stream()
                .filter(j -> j.getRoundNumber() == lockedRound)
                .findFirst()
                .map(j -> j.getMatches().stream()
                        .anyMatch(m -> MatchStatus.PENDING.equals(m.getStatus())))
                .orElse(false);
        if (stillLocked) {
            throw new IllegalStateException(
                    "'" + pokemonName + "' está bloqueado hasta que finalice la jornada actual.");
        }
    }

    @Override
    public Class<ProposeTradeCommand> commandType() {
        return ProposeTradeCommand.class;
    }
}
