package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Push "te toca en el draft" al jugador en turno. Se llama tras empezar el draft y tras cada pick (manual o
 * automático); el envío real se hace tras el commit, así que un pick que falla no avisa a nadie.
 */
@Service
public class DraftTurnNotifier {

    private final UserRepository userRepository;
    private final PushNotificationPort pushNotificationPort;

    public DraftTurnNotifier(UserRepository userRepository, PushNotificationPort pushNotificationPort) {
        this.userRepository = userRepository;
        this.pushNotificationPort = pushNotificationPort;
    }

    public void notifyCurrentTurn(DraftEntity draft, LeagueEntity league) {
        if (draft.getStatus() != DraftStatus.IN_PROGRESS || draft.getTurnOrder() == null
                || draft.getCurrentTurnIndex() >= draft.getTurnOrder().size()) {
            return;
        }
        UserEntity user = userRepository.findByUsername(draft.getTurnOrder().get(draft.getCurrentTurnIndex()));
        if (user == null || user.getFcmTokens() == null || user.getFcmTokens().isEmpty()) {
            return;
        }
        String leagueName = league != null && league.getName() != null ? league.getName() : "Tu liga";
        String timer = Optional.ofNullable(league)
                .flatMap(l -> DraftTurnTimeoutService.turnDeadline(draft, l))
                .map(deadline -> " Tienes " + describe(draft.getCurrentTurnStartedAt().until(deadline,
                        ChronoUnit.SECONDS)) + ".")
                .orElse("");
        pushNotificationPort.send(user.getFcmTokens(), "¡Te toca en el draft!",
                leagueName + " · ronda " + draft.getCurrentRound() + ": elige tu Pokémon." + timer);
    }

    static String describe(long seconds) {
        if (seconds < 120) return seconds + " s";
        if (seconds < 7200) return (seconds / 60) + " min";
        return (seconds / 3600) + " h";
    }
}
