package org.factcast.factus.projector;

import java.util.function.Function;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;

// TODO find a better name
public interface ProjectorLens {
  Function<Fact, Object> parameterTransformerFor(Class<?> type);

  void beforeApply(Projection p, Fact f);

  void afterApply(Projection p, Fact f);

  void onCatchup(Projection p);
}
