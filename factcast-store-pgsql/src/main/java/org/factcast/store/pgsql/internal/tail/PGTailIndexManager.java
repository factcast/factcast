package org.factcast.store.pgsql.internal.tail;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.springframework.scheduling.annotation.Scheduled;

public interface PGTailIndexManager extends FastForwardTarget {
  @Scheduled(cron = "* * */1 * * *")
  @SchedulerLock(name = "triggerTailCreation", lockAtMostFor = "120m")
  void triggerTailCreation();
}
