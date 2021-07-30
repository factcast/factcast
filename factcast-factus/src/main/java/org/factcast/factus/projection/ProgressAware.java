package org.factcast.factus.projection;

import org.slf4j.LoggerFactory;

public interface ProgressAware {
  default void catchupPercentage(int percent) {
    LoggerFactory.getLogger(getClass()).debug("catchup progress {}%", percent);
  }
}
