package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.league.CurrentDraftService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.InviteRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * El link de invitación es de varios usos: sirve para todos los que lo reciban hasta que caduca (TTL en Redis).
 * Deja de admitir miembros nuevos en cuanto empieza el draft; un miembro que lo reabre simplemente entra.
 */
@Service
public class RedeemInviteCommandHandler
        implements CommandHandler<RedeemInviteCommand, RedeemInviteResponse> {

    private static final Set<DraftStatus> STARTED = EnumSet.of(DraftStatus.IN_PROGRESS, DraftStatus.COMPLETED);

    private final InviteRepository inviteRepository;
    private final LeagueRepository leagueRepository;
    private final CurrentDraftService currentDraftService;

    public RedeemInviteCommandHandler(InviteRepository inviteRepository, LeagueRepository leagueRepository,
                                      CurrentDraftService currentDraftService) {
        this.inviteRepository = inviteRepository;
        this.leagueRepository = leagueRepository;
        this.currentDraftService = currentDraftService;
    }

    @Override
    public RedeemInviteResponse handle(RedeemInviteCommand command) {
        String leagueId = inviteRepository.findLeagueId(command.token());
        if (leagueId == null) throw new IllegalArgumentException("Enlace de invitación no válido o caducado");

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        boolean alreadyMember = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(command.username()));
        if (alreadyMember) return new RedeemInviteResponse(leagueId, true);

        if (currentDraftService.statusOf(leagueId).filter(STARTED::contains).isPresent()) {
            throw new IllegalStateException("El draft de esta liga ya ha empezado: no admite nuevos miembros");
        }

        leagueRepository.addMember(leagueId, new LeagueMember(command.username(), LeagueRole.USER, 0));
        return new RedeemInviteResponse(leagueId, false);
    }

    @Override
    public Class<RedeemInviteCommand> commandType() {
        return RedeemInviteCommand.class;
    }
}
