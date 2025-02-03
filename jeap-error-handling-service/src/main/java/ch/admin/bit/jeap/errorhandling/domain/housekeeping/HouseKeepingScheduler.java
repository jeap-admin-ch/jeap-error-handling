package ch.admin.bit.jeap.errorhandling.domain.housekeeping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class HouseKeepingScheduler {

    final HouseKeepingSchedulerConfigProperties houseKeepingSchedulerConfigProperties;
    final HouseKeepingService houseKeepingService;

    @Scheduled(cron = "#{@houseKeepingSchedulerConfigProperties.cronExpression}")
    @SchedulerLock(name = "HouseKeepingScheduler_execute", lockAtLeastFor = "#{@houseKeepingSchedulerConfigProperties.lockAtLeast.toString()}", lockAtMostFor = "#{@houseKeepingSchedulerConfigProperties.lockAtMost.toString()}")
    public void execute() {
        LockAssert.assertLocked();
        log.info("Housekeeping started");
        houseKeepingService.cleanup();
        log.info("Housekeeping ended");
    }

}
