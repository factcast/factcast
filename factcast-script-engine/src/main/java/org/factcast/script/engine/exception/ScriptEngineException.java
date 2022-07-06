package org.factcast.script.engine.exception;

import lombok.NonNull;

public class ScriptEngineException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ScriptEngineException(@NonNull Throwable e) {
    super(e);
  }

  public ScriptEngineException(String message) {
    super(message);
  }
}
