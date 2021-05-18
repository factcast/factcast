package org.factcast.test;

import java.util.ServiceLoader;

public interface BetweenTestsEraser {

  void wipe(Object testInstance);

  static void visitAll(Object testInstance) {
    ServiceLoader.load(BetweenTestsEraser.class).forEach(w -> w.wipe(testInstance));
  }
}
