package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class SetStealPriceCommandHandler implements CommandHandler<SetStealPriceCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberService leagueMemberService;
    private final TierPricingService tierPricingService;

    public SetStealPriceCommandHandler(DraftRepository draftRepository,
                                       ClosedListRepository closedListRepository,
                                       LeagueRepository leagueRepository,
                                       LeagueMemberService leagueMemberService,
                                       TierPricingService tierPricingService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.leagueMemberService = leagueMemberService;
        this.tierPricingService = tierPricingService;
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
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        // Compute current effective price
        LeagueSettings settings = league.getSettings();
        int currentEffectivePrice;
        if (pick.getCustomStealPrice() != null) {
            currentEffectivePrice = pick.getCustomStealPrice();
        } else {
            ClosedListEntity entry = closedListRepository
                    .findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                    .orElse(null);
            Tier tier = entry != null ? entry.getTier() : null;
            currentEffectivePrice = tierPricingService.priceForTier(settings, tier);
        }

        if (newPrice <= currentEffectivePrice) {
            throw new IllegalArgumentException(
                    "El nuevo precio (" + newPrice + ") debe ser mayor que el precio actual (" + currentEffectivePrice + ")");
        }

        int investment = newPrice - currentEffectivePrice;
        LeagueMember member = leagueMemberService.requireMember(league, username);

        if (member.getCoinBalance() < investment) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas invertir " + investment +
                    " pero tienes " + member.getCoinBalance() + ".");
        }

        member.setCoinBalance(member.getCoinBalance() - investment);
        leagueRepository.save(league);

        pick.setCustomStealPrice(newPrice);

        try {
            draftRepository.save(draft);
        } catch (OptimisticLockingFailureException exception) {
            compensatePriceChange(league, member, investment);
            throw new IllegalStateException("Otro jugador modificó el draft al mismo tiempo. Inténtalo de nuevo.", exception);
        } catch (RuntimeException exception) {
            compensatePriceChange(league, member, investment);
            throw exception;
        }

        return null;
    }

    /**
     * Revierte la inversión en monedas si draftRepository.save(draft) falla —
     * el nuevo customStealPrice solo existe en el draft in-memory descartado, no se persiste.
     */
    private void compensatePriceChange(LeagueEntity league, LeagueMember member, int investment) {
        member.setCoinBalance(member.getCoinBalance() + investment);
        leagueRepository.save(league);
    }

    @Override
    public Class<SetStealPriceCommand> commandType() {
        return SetStealPriceCommand.class;
    }
}
