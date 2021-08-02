package org.factcast.factus.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ProgressAware {
  default void catchupPercentage(int percent) {
    getLogger().debug("catchup progress {}%", percent);
  }

  default Logger getLogger() {
    return LoggerFactory.getLogger(getClass());
  }
}
