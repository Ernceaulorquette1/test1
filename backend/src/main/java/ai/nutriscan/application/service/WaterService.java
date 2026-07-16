package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.MiscDtos.*;
import ai.nutriscan.domain.model.User;
import ai.nutriscan.domain.model.WaterLog;
import ai.nutriscan.domain.repository.WaterLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class WaterService {

    /** Meta diaria recomendada por defecto (ml). */
    public static final int DEFAULT_GOAL_ML = 2500;

    private final WaterLogRepository logs;
    private final CurrentUserService currentUser;

    public WaterService(WaterLogRepository logs, CurrentUserService currentUser) {
        this.logs = logs;
        this.currentUser = currentUser;
    }

    @Transactional
    public WaterDayResponse add(int ml) {
        User user = currentUser.require();
        WaterLog log = new WaterLog();
        log.setUser(user);
        log.setLogDate(LocalDate.now(ZoneOffset.UTC));
        log.setMl(ml);
        logs.save(log);
        return today();
    }

    @Transactional(readOnly = true)
    public WaterDayResponse today() {
        User user = currentUser.require();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var entries = logs.findByUserIdAndLogDateOrderByLoggedAtAsc(user.getId(), today).stream()
                .map(w -> new WaterEntry(w.getLoggedAt(), w.getMl()))
                .toList();
        return new WaterDayResponse(today.toString(),
                logs.totalMlForDate(user.getId(), today), DEFAULT_GOAL_ML, entries);
    }
}
