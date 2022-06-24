package org.factcast.store.internal.query;

import java.sql.Statement;

import org.factcast.core.store.CascadingDisposal;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CancelStatementListener implements CascadingDisposal.Listener {

  private Statement statement;

  @Override
  public void onDispose() throws Exception {
    if (statement != null) {
      log.info("Canceling statement " + statement);
      statement.cancel();
    }
  }
}
