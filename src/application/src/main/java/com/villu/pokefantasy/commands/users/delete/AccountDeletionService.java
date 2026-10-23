package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.commands.users.DeletedUser;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.league.LeagueMemberRemoval;
import com.villu.pokefantasy.ports.DeletedUsernamePort;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Borra una cuenta (RGPD art. 17, spec 2026-10-18-borrado-cuenta-privacidad), la pida el usuario o el admin
 * (L5). En cada liga sale como en una expulsión ({@link LeagueMemberRemoval}): sus Pokémon vuelven al banquillo.
 * Si era el único admin, lo pasa a ser el primer miembro que queda; si era el único miembro, la liga se borra. Su
 * nombre pasa a {@link DeletedUser#NAME} en todas las ligas. Devuelve las ligas que siguen existiendo. Quien lo
 * llama comprueba los permisos (contraseña o rol de admin).
 */
@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final RefreshTokenPort refreshTokenPort;
    private final AvatarRepository avatarRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRemoval leagueMemberRemoval;
    private final DraftRepository draftRepository;
    private final ScheduleRepository scheduleRepository;
    private final TradeRepository tradeRepository;
    private final ActivityEventRepository activityEventRepository;
    private final ClosedListRepository closedListRepository;
    private final DeletedUsernamePort deletedUsernamePort;
    private final TokenPort tokenPort;

    public AccountDeletionService(UserRepository userRepository, RefreshTokenPort refreshTokenPort,
                                  AvatarRepository avatarRepository, LeagueRepository leagueRepository,
                                  LeagueMemberRemoval leagueMemberRemoval, DraftRepository draftRepository,
                                  ScheduleRepository scheduleRepository, TradeRepository tradeRepository,
                                  ActivityEventRepository activityEventRepository,
                                  ClosedListRepository closedListRepository,
                                  DeletedUsernamePort deletedUsernamePort, TokenPort tokenPort) {
        this.userRepository = userRepository;
        this.refreshTokenPort = refreshTokenPort;
        this.avatarRepository = avatarRepository;
        this.leagueRepository = leagueRepository;
        this.leagueMemberRemoval = leagueMemberRemoval;
        this.draftRepository = draftRepository;
        this.scheduleRepository = scheduleRepository;
        this.tradeRepository = tradeRepository;
        this.activityEventRepository = activityEventRepository;
        this.closedListRepository = closedListRepository;
        this.deletedUsernamePort = deletedUsernamePort;
        this.tokenPort = tokenPort;
    }

    public List<String> delete(String username) {
        // Lo primero: el JWT de acceso solo lleva el nombre y no se puede revocar, así que hasta que caduque nadie
        // puede registrarse con ese nombre (si no, ese JWT entraría en la cuenta nueva). Si no se puede reservar,
        // lanza antes de tocar nada.
        deletedUsernamePort.reserve(username, tokenPort.accessTokenTtl());

        List<String> remaining = new ArrayList<>();
        for (LeagueEntity league : leagueRepository.findByMemberUsername(username)) {
            if (league.getMembers().size() <= 1) {
                deleteLeague(league.getId());
            } else {
                leaveLeague(league, username);
                remaining.add(league.getId());
            }
        }

        // Sin filtrar por liga: también en las que ya dejó (salir o ser expulsado no anonimiza ni cancela nada).
        tradeRepository.cancelPending(username, Instant.now());
        tradeRepository.anonymizeUser(username, DeletedUser.NAME);
        activityEventRepository.anonymizeUser(username, DeletedUser.NAME);
        scheduleRepository.anonymizeUser(username, DeletedUser.NAME);
        draftRepository.anonymizeUser(username, DeletedUser.NAME);
        closedListRepository.anonymizeUser(username, DeletedUser.NAME);
        leagueRepository.anonymizeUser(username, DeletedUser.NAME);

        avatarRepository.deleteByUsername(username);
        // Dentro de la transacción, como al cambiar la contraseña; el controller lo repite tras el commit.
        refreshTokenPort.revokeAll(username);
        userRepository.deleteByUsername(username);
        return remaining;
    }

    private void deleteLeague(String leagueId) {
        draftRepository.findAllByLeagueId(leagueId).forEach(draftRepository::delete);
        scheduleRepository.deleteByLeagueId(leagueId);
        tradeRepository.deleteByLeagueId(leagueId);
        activityEventRepository.deleteByLeagueId(leagueId);
        closedListRepository.deleteAllByLeagueId(leagueId);
        leagueRepository.deleteById(leagueId);
    }

    private void leaveLeague(LeagueEntity league, String username) {
        leagueMemberRemoval.remove(league, username);
        // Se relee: removeMember actualiza el documento en Mongo, no la entidad cargada.
        leagueRepository.findById(league.getId()).ifPresent(updated -> {
            List<LeagueMember> members = updated.getMembers();
            if (!members.isEmpty() && members.stream().noneMatch(m -> m.getLeagueRole() == LeagueRole.ADMIN)) {
                members.get(0).setLeagueRole(LeagueRole.ADMIN);
                leagueRepository.save(updated);
            }
        });
    }
}
