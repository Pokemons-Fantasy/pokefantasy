package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.response.AdminUserResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** Moderación (L5): usuarios cuyo nombre empieza por {@code q} (como mucho 20, lo limita el repositorio). */
@Service
public class SearchUsersForAdminCommandHandler implements CommandHandler<SearchUsersForAdminCommand, List<AdminUserResponse>> {

    static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;

    public SearchUsersForAdminCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<AdminUserResponse> handle(SearchUsersForAdminCommand command) {
        String q = command.q() == null ? "" : command.q().strip();
        if (q.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return userRepository.findByUsernamePrefix(q).stream()
                .map(user -> new AdminUserResponse(user.getName(), user.getAvatarVersion(), user.getRole() == Role.ADMIN))
                .toList();
    }

    @Override
    public Class<SearchUsersForAdminCommand> commandType() {
        return SearchUsersForAdminCommand.class;
    }
}
