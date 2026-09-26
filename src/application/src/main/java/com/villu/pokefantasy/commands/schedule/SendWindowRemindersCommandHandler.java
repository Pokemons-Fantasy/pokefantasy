package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

@Service
public class SendWindowRemindersCommandHandler implements CommandHandler<SendWindowRemindersCommand, Boolean> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final WindowReminderService windowReminderService;

    public SendWindowRemindersCommandHandler(ScheduleRepository scheduleRepository,
                                             LeagueRepository leagueRepository,
                                             WindowReminderService windowReminderService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.windowReminderService = windowReminderService;
    }

    @Override
    public Boolean handle(SendWindowRemindersCommand command) {
        // Se relee y se vuelve a comprobar dentro de la transacción: si otra instancia ya avisó, el
        // calendario lo refleja y no se repite (y si guardan a la vez, @Version hace reintentar a una).
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId()).orElse(null);
        LeagueEntity league = leagueRepository.findById(command.leagueId()).orElse(null);
        if (schedule == null || league == null || !windowReminderService.sendDue(schedule, league)) {
            return false;
        }
        scheduleRepository.save(schedule);
        return true;
    }

    @Override
    public Class<SendWindowRemindersCommand> commandType() {
        return SendWindowRemindersCommand.class;
    }
}
