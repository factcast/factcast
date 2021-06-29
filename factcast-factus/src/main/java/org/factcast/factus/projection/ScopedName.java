package org.factcast.factus.projection;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.utils.ClassUtils;

@RequiredArgsConstructor(staticName = "of")
public class ScopedName {
  private static final String NAME_SEPARATOR = "_";

  private final String key;

  public static ScopedName forClass(Class<?> clazz) {
    val metaData = ProjectionMetaData.Resolver.resolveFor(clazz);

    String name = metaData.name();
    if (name.isEmpty()) {
      name = ClassUtils.getNameFor(clazz);
    }

    return ScopedName.of(name + NAME_SEPARATOR + metaData.serial());
  }

  public ScopedName with(@NonNull String addition) {
    return ScopedName.of(key + NAME_SEPARATOR + addition);
  }

  public String toString() {
    return key;
  }
}
