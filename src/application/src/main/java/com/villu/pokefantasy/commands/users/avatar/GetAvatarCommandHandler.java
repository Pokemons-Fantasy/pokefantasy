package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.response.AvatarImageResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Foto de perfil de un usuario. Solo la ven él mismo y quienes comparten liga con él (como la búsqueda
 * de usuarios, no permite sondear cuentas ajenas); para los demás es como si no tuviera foto.
 */
@Service
public class GetAvatarCommandHandler implements CommandHandler<GetAvatarCommand, Optional<AvatarImageResponse>> {

    private final AvatarRepository avatarRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;

    public GetAvatarCommandHandler(AvatarRepository avatarRepository, LeagueMembershipGuard leagueMembershipGuard) {
        this.avatarRepository = avatarRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public Optional<AvatarImageResponse> handle(GetAvatarCommand command) {
        if (!leagueMembershipGuard.sharesLeague(command.requestingUsername(), command.username())) {
            return Optional.empty();
        }
        return avatarRepository.findByUsername(command.username())
                .map(a -> new AvatarImageResponse(a.getData(), a.getContentType()));
    }

    @Override
    public Class<GetAvatarCommand> commandType() {
        return GetAvatarCommand.class;
    }
}
