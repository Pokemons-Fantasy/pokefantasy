package com.villu.pokefantasy.commands.users.search;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchUsersCommandHandler
        implements CommandHandler<SearchUsersCommand, List<String>> {

    private final UserRepository userRepository;
    private final LeagueRepository leagueRepository;

    public SearchUsersCommandHandler(UserRepository userRepository, LeagueRepository leagueRepository) {
        this.userRepository = userRepository;
        this.leagueRepository = leagueRepository;
    }

    @Override
    public List<String> handle(SearchUsersCommand command) {
        if (command.prefix() == null || command.prefix().length() < 2) return List.of();

        var matches = userRepository.findByUsernamePrefix(command.prefix());

        Set<String> existing = Collections.emptySet();
        if (command.leagueId() != null) {
            existing = leagueRepository.findById(command.leagueId())
                    .map(league -> league.getMembers().stream()
                            .map(LeagueMember::getUsername)
                            .collect(Collectors.toSet()))
                    .orElse(Collections.emptySet());
        }
        final Set<String> existingFinal = existing;
        return matches.stream()
                .map(u -> u.getName())
                .filter(u -> !existingFinal.contains(u))
                .limit(8)
                .collect(Collectors.toList());
    }

    @Override
    public Class<SearchUsersCommand> commandType() {
        return SearchUsersCommand.class;
    }
}
