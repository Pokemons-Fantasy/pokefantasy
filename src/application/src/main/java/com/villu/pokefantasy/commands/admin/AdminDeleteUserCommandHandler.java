package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.commands.users.delete.AccountDeletionService;
import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Moderación (L5): el admin global borra una cuenta sin su contraseña, igual que el borrado propio
 * ({@link AccountDeletionService}). No puede borrar a otro admin. Devuelve las ligas que siguen existiendo.
 */
@Service
@Slf4j
public class AdminDeleteUserCommandHandler implements CommandHandler<AdminDeleteUserCommand, List<String>> {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    public AdminDeleteUserCommandHandler(UserRepository userRepository, AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
    }

    @Override
    public List<String> handle(AdminDeleteUserCommand command) {
        UserEntity target = userRepository.findByUsername(command.targetUsername());
        if (target == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new ForbiddenOperationException("No se puede borrar a otro administrador.");
        }
        log.info("Admin {} deleted user {}", command.adminUsername(), command.targetUsername());
        return accountDeletionService.delete(command.targetUsername());
    }

    @Override
    public Class<AdminDeleteUserCommand> commandType() {
        return AdminDeleteUserCommand.class;
    }
}
