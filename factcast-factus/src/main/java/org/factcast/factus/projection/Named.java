package org.factcast.factus.projection;

public interface Named {
  default ScopedName getScopedName() {
    return ScopedName.fromProjectionMetaData(getClass());
  }
}
