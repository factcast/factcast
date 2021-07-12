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

  public static ScopedName fromProjectionMetaData(Class<?> clazz) {
    val metaData =
        ProjectionMetaData.Resolver.resolveFor(clazz)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        clazz.getName()
                            + " must be annotated by "
                            + ProjectionMetaData.class.getName()));

    String name = metaData.name();
    if (name.isEmpty()) {
      name = ClassUtils.getNameFor(clazz);
    }

    return ScopedName.of(name + NAME_SEPARATOR + metaData.serial());
  }

  public static ScopedName of(@NonNull String name, long serial) {
    return ScopedName.of(name + NAME_SEPARATOR + serial);
  }

  public ScopedName with(@NonNull String postfix) {
    if (postfix.trim().isEmpty()) {
      throw new IllegalArgumentException("postfix must not be empty");
    }
    return ScopedName.of(key + NAME_SEPARATOR + postfix);
  }

  @Override
  public String toString() {
    return key;
  }
}
