package org.factcast.factus.projector;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;

public interface ProjectorPlugin {

  List<ProjectorPlugin> discovered =
      ImmutableList.sortedCopyOf(
          Comparator.comparing(ProjectorPlugin::order), ServiceLoader.load(ProjectorPlugin.class));

  default int order() {
    return 0;
  }

  /**
   * @param p the projection in question
   * @return empty list if not applicable to p
   */
  Collection<ProjectorLens> lensFor(@NonNull Projection p);
}
