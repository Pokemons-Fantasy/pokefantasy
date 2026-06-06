package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

@Service
public class SetLeagueMvpCommandHandler implements CommandHandler<SetLeagueMvpCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;

    public SetLeagueMvpCommandHandler(LeagueRepository leagueRepository, DraftRepository draftRepository) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    public Void handle(SetLeagueMvpCommand command) {
        if (command.pokemonName() == null || command.pokemonName().isBlank()) {
            throw new IllegalArgumentException("El nombre del Pokémon no puede estar vacío");
        }

        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        // Draft must be COMPLETED
        var draft = draftRepository.findLatestByLeagueId(command.leagueId())
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(
                        "Solo puedes elegir MVP cuando el draft está completado"));

        // Pokemon must be in current picks of this user (case-insensitive)
        boolean ownsPokemon = draft.getPicks() != null && draft.getPicks().stream()
                .anyMatch(p -> command.username().equals(p.getUsername())
                        && command.pokemonName().equalsIgnoreCase(p.getPokemonName()));

        if (!ownsPokemon) {
            throw new IllegalArgumentException(
                    "'" + command.pokemonName() + "' no está en tu equipo actual");
        }

        // Persist
        if (league.getMembers() == null) {
            throw new IllegalStateException("La liga no tiene miembros");
        }

        LeagueMember member = league.getMembers().stream()
                .filter(m -> command.username().equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + command.username()));

        member.setCustomMvpPokemon(command.pokemonName());
        leagueRepository.save(league);
        return null;
    }

    @Override
    public Class<SetLeagueMvpCommand> commandType() {
        return SetLeagueMvpCommand.class;
    }
}
