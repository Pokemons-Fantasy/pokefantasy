package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftTurnNotifierTest {

    @Mock private UserRepository userRepository;
    @Mock private PushNotificationPort pushNotificationPort;

    private DraftTurnNotifier notifier;
    private DraftEntity draft;
    private LeagueEntity league;

    @BeforeEach
    void setUp() {
        notifier = new DraftTurnNotifier(userRepository, pushNotificationPort);
        draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(List.of("ash", "brock"));
        draft.setCurrentTurnIndex(1);
        draft.setCurrentRound(3);
        draft.setCurrentTurnStartedAt(Instant.now());
        league = new LeagueEntity();
        league.setName("Liga Kanto");
        UserEntity brock = new UserEntity();
        brock.setFcmTokens(new ArrayList<>(List.of("brock-phone")));
        lenient().when(userRepository.findByUsername("brock")).thenReturn(brock);
    }

    @Test
    void notifiesThePlayerInTurn_withLeagueRoundAndTimer() {
        LeagueSettings settings = LeagueSettings.defaults();
        settings.setTurnTimerSeconds(120);
        league.setSettings(settings);

        notifier.notifyCurrentTurn(draft, league);

        verify(pushNotificationPort).send(List.of("brock-phone"), "¡Te toca en el draft!",
                "Liga Kanto · ronda 3: elige tu Pokémon. Tienes 2 min.");
    }

    @Test
    void withoutTimerOrLeague_noTimeHint() {
        notifier.notifyCurrentTurn(draft, null);

        verify(pushNotificationPort).send(List.of("brock-phone"), "¡Te toca en el draft!",
                "Tu liga · ronda 3: elige tu Pokémon.");
    }

    @Test
    void playerWithoutDevices_orDraftNotInProgress_sendsNothing() {
        when(userRepository.findByUsername("brock")).thenReturn(new UserEntity());
        notifier.notifyCurrentTurn(draft, league);

        draft.setStatus(DraftStatus.COMPLETED);
        notifier.notifyCurrentTurn(draft, league);

        when(userRepository.findByUsername("brock")).thenReturn(null);
        draft.setStatus(DraftStatus.IN_PROGRESS);
        notifier.notifyCurrentTurn(draft, league);

        verify(pushNotificationPort, never()).send(any(), any(), any());
    }

    @Test
    void describe_picksAReadableUnit() {
        assertThat(DraftTurnNotifier.describe(45)).isEqualTo("45 s");
        assertThat(DraftTurnNotifier.describe(300)).isEqualTo("5 min");
        assertThat(DraftTurnNotifier.describe(86_400)).isEqualTo("24 h");
    }
}
