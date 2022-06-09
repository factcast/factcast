package org.factcast.core;

public class DuplicateFactException extends RuntimeException {
  public DuplicateFactException(String msg) {
    super(msg);
  }
}
