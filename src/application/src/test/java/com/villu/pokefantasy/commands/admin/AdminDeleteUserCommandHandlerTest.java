package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.commands.users.delete.AccountDeletionService;
import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminDeleteUserCommandHandlerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountDeletionService accountDeletionService = mock(AccountDeletionService.class);
    private final AdminDeleteUserCommandHandler handler =
            new AdminDeleteUserCommandHandler(userRepository, accountDeletionService);

    private static UserEntity user(String name, Role role) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setRole(role);
        return user;
    }

    @Test
    void adminDelete_unknownUser_isNotFound() {
        assertThatThrownBy(() -> handler.handle(new AdminDeleteUserCommand("root", "ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
        verifyNoInteractions(accountDeletionService);
    }

    @Test
    void adminDelete_otherAdmin_isForbidden_andDeletesNothing() {
        when(userRepository.findByUsername("boss")).thenReturn(user("boss", Role.ADMIN));

        assertThatThrownBy(() -> handler.handle(new AdminDeleteUserCommand("root", "boss")))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("No se puede borrar a otro administrador.");
        verifyNoInteractions(accountDeletionService);
    }

    @Test
    void adminDelete_regularUser_delegatesAndReturnsTheRemainingLeagues() {
        when(userRepository.findByUsername("troll")).thenReturn(user("troll", Role.USER));
        when(accountDeletionService.delete("troll")).thenReturn(List.of("l1"));

        assertThat(handler.handle(new AdminDeleteUserCommand("root", "troll"))).containsExactly("l1");
        assertThat(handler.commandType()).isEqualTo(AdminDeleteUserCommand.class);
    }
}
