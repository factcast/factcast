package org.factcast.factus.projector;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;

public interface ProjectorPlugin {

  List<ProjectorPlugin> discovered =
      ImmutableList.sortedCopyOf(
          Comparator.comparing(ProjectorPlugin::weight), ServiceLoader.load(ProjectorPlugin.class));

  int weight();

  boolean appliesTo(Class<? extends Projection> projectionClazz);

  static List<ProjectorPlugin> discover() {
    return discovered;
  }

  Function<Fact, Object> parameterTransformerFor(Class<?> type);
}
