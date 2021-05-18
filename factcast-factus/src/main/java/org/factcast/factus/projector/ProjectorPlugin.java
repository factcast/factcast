package org.factcast.factus.projector;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.ServiceLoader;
import org.factcast.factus.projection.Projection;

public interface ProjectorPlugin {

  boolean appliesTo(Class<? extends Projection> projectionClazz);

  static List<ProjectorPlugin> discover() {
    return Lists.newArrayList(ServiceLoader.load(ProjectorPlugin.class).iterator());
  }
}
