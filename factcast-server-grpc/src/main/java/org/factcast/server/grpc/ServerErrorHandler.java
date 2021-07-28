package org.factcast.server.grpc;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerErrorHandler {

  public void log(Throwable e, @NonNull String id) {
    // TODO categorize and either skip, or log to a matching loglevel :D
    log.info("{} onError – sending Error notification {}", id, e.getMessage());
  }
}
