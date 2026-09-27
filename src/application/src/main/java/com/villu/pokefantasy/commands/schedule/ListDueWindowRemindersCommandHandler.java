package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ListDueWindowRemindersCommandHandler implements CommandHandler<ListDueWindowRemindersCommand, List<String>> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final WindowReminderService windowReminderService;

    public ListDueWindowRemindersCommandHandler(ScheduleRepository scheduleRepository,
                                                LeagueRepository leagueRepository,
                                                WindowReminderService windowReminderService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.windowReminderService = windowReminderService;
    }

    @Override
    public List<String> handle(ListDueWindowRemindersCommand command) {
        List<String> leagueIds = new ArrayList<>();
        for (ScheduleEntity schedule : scheduleRepository.findAll()) {
            leagueRepository.findById(schedule.getLeagueId())
                    .filter(league -> !windowReminderService.due(schedule, league.getSettings()).isEmpty())
                    .ifPresent(league -> leagueIds.add(league.getId()));
        }
        return leagueIds;
    }

    @Override
    public Class<ListDueWindowRemindersCommand> commandType() {
        return ListDueWindowRemindersCommand.class;
    }
}
