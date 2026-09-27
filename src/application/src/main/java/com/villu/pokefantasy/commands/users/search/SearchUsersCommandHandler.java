package com.villu.pokefantasy.commands.users.search;

import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Autocompletado de usernames para añadir miembros a una liga. Solo lo puede usar un admin de esa
 * liga (es quien añade miembros); así un usuario cualquiera no puede enumerar todas las cuentas.
 */
@Service
public class SearchUsersCommandHandler
        implements CommandHandler<SearchUsersCommand, List<String>> {

    static final int MIN_PREFIX_LENGTH = 2;
    static final int MAX_RESULTS = 8;

    private final UserRepository userRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public SearchUsersCommandHandler(UserRepository userRepository, LeagueAdminGuard leagueAdminGuard) {
        this.userRepository = userRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public List<String> handle(SearchUsersCommand command) {
        if (command.leagueId() == null || command.leagueId().isBlank()) {
            throw new IllegalArgumentException("leagueId is required");
        }
        Set<String> existing = leagueAdminGuard
                .requireLeagueAdmin(command.leagueId(), command.requestingUsername())
                .getMembers().stream()
                .map(LeagueMember::getUsername)
                .collect(Collectors.toSet());

        if (command.prefix() == null || command.prefix().length() < MIN_PREFIX_LENGTH) return List.of();

        return userRepository.findByUsernamePrefix(command.prefix()).stream()
                .map(u -> u.getName())
                .filter(u -> !existing.contains(u))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    @Override
    public Class<SearchUsersCommand> commandType() {
        return SearchUsersCommand.class;
    }
}
