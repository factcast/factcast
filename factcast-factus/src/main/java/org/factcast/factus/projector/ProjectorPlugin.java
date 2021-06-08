package org.factcast.factus.projector;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.factcast.factus.projection.Projection;

public interface ProjectorPlugin {

  List<ProjectorPlugin> discovered =
      ImmutableList.sortedCopyOf(
          Comparator.comparing(ProjectorPlugin::order), ServiceLoader.load(ProjectorPlugin.class));

  default int order() {
    return 0;
  }

  /**
   * @param p
   * @return null if not applicable to projection p
   */
  @Nullable
  Collection<ProjectorLens> lensFor(Projection p);
}
