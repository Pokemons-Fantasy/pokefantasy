package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

@Service
public class SetStealPriceCommandHandler implements CommandHandler<SetStealPriceCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;

    public SetStealPriceCommandHandler(DraftRepository draftRepository,
                                       ClosedListRepository closedListRepository,
                                       LeagueRepository leagueRepository) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
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
            currentEffectivePrice = priceForTier(settings, tier);
        }

        if (newPrice <= currentEffectivePrice) {
            throw new IllegalArgumentException(
                    "El nuevo precio (" + newPrice + ") debe ser mayor que el precio actual (" + currentEffectivePrice + ")");
        }

        int investment = newPrice - currentEffectivePrice;
        LeagueMember member = league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Member not found: " + username));

        if (member.getCoinBalance() < investment) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas invertir " + investment +
                    " pero tienes " + member.getCoinBalance() + ".");
        }

        member.setCoinBalance(member.getCoinBalance() - investment);
        leagueRepository.save(league);

        pick.setCustomStealPrice(newPrice);
        draftRepository.save(draft);

        return null;
    }

    private int priceForTier(LeagueSettings settings, Tier tier) {
        if (settings == null || tier == null) return 0;
        Integer price = switch (tier) {
            case S -> settings.getPriceTierS();
            case A -> settings.getPriceTierA();
            case B -> settings.getPriceTierB();
            case C -> settings.getPriceTierC();
            case D -> settings.getPriceTierD();
        };
        return price != null ? price : 0;
    }

    @Override
    public Class<SetStealPriceCommand> commandType() {
        return SetStealPriceCommand.class;
    }
}
