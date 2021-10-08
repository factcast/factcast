package org.factcast.store.internal.listen;

import java.util.Iterator;
import java.util.LinkedHashSet;
import org.factcast.store.internal.listen.PgListener.Signal;

public class SignalDeduplicationSet {
  private final LinkedHashSet<Signal> lhs;
  private final int capacity;

  public SignalDeduplicationSet(int capacity) {
    // *2 to prevent rehashing
    this.lhs = new LinkedHashSet<Signal>(capacity * 2, 0.75f);
    this.capacity = capacity;
  }

  public boolean add(Signal e) {
    synchronized (lhs) {
      if (lhs.add(e)) {
        trim(lhs);
        return true;
      }
    }
    return false;
  }

  private void trim(LinkedHashSet<Signal> lhs) {
    if (lhs.size() > capacity) {
      Iterator<Signal> i = lhs.iterator();
      i.next();
      i.remove();
    }
  }
}
