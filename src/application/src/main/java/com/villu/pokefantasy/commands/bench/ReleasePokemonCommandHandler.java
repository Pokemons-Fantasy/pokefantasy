package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ReleasePokemonCommandHandler implements CommandHandler<ReleasePokemonCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;

    public ReleasePokemonCommandHandler(DraftRepository draftRepository,
                                        ClosedListRepository closedListRepository,
                                        LeagueRepository leagueRepository,
                                        UserRepository userRepository,
                                        ScheduleRepository scheduleRepository,
                                        JornadaWindowService jornadaWindowService,
                                        ActivityEventRepository activityEventRepository) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.jornadaWindowService = jornadaWindowService;
        this.activityEventRepository = activityEventRepository;
    }

    @Override
    public Void handle(ReleasePokemonCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonName = command.pokemonName().trim();

        // 1. Draft must be completed
        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(
                        "Solo se pueden liberar pokémon una vez completado el draft"));

        // 2. Swap window must be open
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No hay calendario para esta liga"));
        // 3. Pokemon must belong to this user in this league
        DraftPick pick = draft.getPicks().stream()
                .filter(p -> username.equals(p.getUsername())
                          && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tienes a '" + pokemonName + "' en tu equipo"));

        // 4. Pokemon must not be locked
        if (pick.getLockedUntil() != null && Instant.now().isBefore(pick.getLockedUntil())) {
            throw new IllegalStateException(
                    "'" + pokemonName + "' está bloqueado hasta " + pick.getLockedUntil());
        }

        // 5. Calculate reward: tier comes from ClosedList (DraftPick has no tier field)
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada: " + leagueId));

        if (!jornadaWindowService.isSwapWindowOpen(schedule, league.getSettings())) {
            throw new IllegalStateException(
                    "La ventana de intercambio está cerrada. Solo puedes liberar pokémon en la ventana de swap.");
        }
        LeagueSettings settings = league.getSettings();

        ClosedListEntity entry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElse(null);
        Tier tier = entry != null ? entry.getTier() : null;
        int reward = priceForTier(settings, tier) / 2;

        // --- Execute ---

        // Remove DraftPick
        draft.getPicks().remove(pick);
        draftRepository.save(draft);

        // Remove from UserEntity.pokemons for this league
        UserEntity user = userRepository.findByUsername(username);
        if (user != null && user.getPokemons() != null) {
            user.getPokemons().removeIf(p ->
                    leagueId.equals(p.getLeagueId()) && pokemonName.equalsIgnoreCase(p.getName()));
            userRepository.updateUserWithPokemons(user);
        }

        // Add coins to member (reward = 0 if tier unknown, safe fallback)
        LeagueMember member = league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Member not found: " + username));
        member.setCoinBalance(member.getCoinBalance() + reward);
        leagueRepository.save(league);

        // Activity event
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.POKEMON_RELEASED)
                .actorUsername(username)
                .pokemonName(pokemonName)
                .coinsAmount(reward)
                .createdAt(Instant.now())
                .build());

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
    public Class<ReleasePokemonCommand> commandType() {
        return ReleasePokemonCommand.class;
    }
}
