package org.factcast.core.store;

import lombok.NonNull;

public interface CascadingDisposal {
  void register(@NonNull Listener s);

  void unregister(@NonNull Listener s);

  void disposeAll();

  interface Listener {
    void onDispose() throws Exception;
  }
}
