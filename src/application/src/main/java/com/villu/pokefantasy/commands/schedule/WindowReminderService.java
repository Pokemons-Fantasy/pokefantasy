package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aviso por push a todos los miembros de una liga cuando faltan menos de {@link #LEAD} para que cierre la
 * ventana de robos o la de swaps de la jornada. Se avisa una vez por cierre: la jornada apunta de qué
 * cierre ya avisó ({@code stealReminderSentFor} / {@code swapReminderSentFor}).
 */
@Service
public class WindowReminderService {

    static final Duration LEAD = Duration.ofHours(3);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    enum Window {
        STEAL("robos", "robar"),
        SWAP("swaps", "hacer swaps o comprar en la banca");

        final String name;
        final String action;

        Window(String name, String action) {
            this.name = name;
            this.action = action;
        }
    }

    /** Aviso pendiente: qué ventana, en qué jornada y cuándo cierra. */
    record Due(Window window, Jornada jornada, LocalDateTime deadline) {}

    private final JornadaWindowService windowService;
    private final UserRepository userRepository;
    private final PushNotificationPort pushNotificationPort;

    public WindowReminderService(JornadaWindowService windowService, UserRepository userRepository,
                                 PushNotificationPort pushNotificationPort) {
        this.windowService = windowService;
        this.userRepository = userRepository;
        this.pushNotificationPort = pushNotificationPort;
    }

    /** Ventanas abiertas que cierran en menos de {@link #LEAD} y de cuyo cierre aún no se avisó. */
    List<Due> due(ScheduleEntity schedule, LeagueSettings settings) {
        List<Due> due = new ArrayList<>();
        Jornada active = windowService.getActiveJornada(schedule).orElse(null);
        if (active == null) {
            return due;
        }
        LocalDateTime remindFrom = windowService.now().plus(LEAD);
        for (Window window : Window.values()) {
            windowService.openWindowDeadline(schedule, window == Window.STEAL, settings)
                    .filter(deadline -> deadline.isBefore(remindFrom))
                    .filter(deadline -> !deadline.toString().equals(sentFor(active, window)))
                    .ifPresent(deadline -> due.add(new Due(window, active, deadline)));
        }
        return due;
    }

    /** Envía los avisos pendientes a los miembros de la liga y los marca en la jornada (sin guardar). */
    boolean sendDue(ScheduleEntity schedule, LeagueEntity league) {
        List<Due> due = due(schedule, league.getSettings());
        if (due.isEmpty()) {
            return false;
        }
        List<String> tokens = league.getMembers().stream()
                .map(LeagueMember::getUsername)
                .map(userRepository::findByUsername)
                .filter(Objects::nonNull)
                .map(UserEntity::getFcmTokens)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
        for (Due reminder : due) {
            pushNotificationPort.send(tokens,
                    "⏰ Cierra la ventana de " + reminder.window().name,
                    league.getName() + ": tienes hasta las " + HOUR.format(reminder.deadline())
                            + " para " + reminder.window().action + ".");
            markSent(reminder);
        }
        return true;
    }

    private static String sentFor(Jornada jornada, Window window) {
        return window == Window.STEAL ? jornada.getStealReminderSentFor() : jornada.getSwapReminderSentFor();
    }

    private static void markSent(Due reminder) {
        if (reminder.window() == Window.STEAL) {
            reminder.jornada().setStealReminderSentFor(reminder.deadline().toString());
        } else {
            reminder.jornada().setSwapReminderSentFor(reminder.deadline().toString());
        }
    }
}
