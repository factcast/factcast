package org.factcast.factus.spring.tx;

import com.google.auto.service.AutoService;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;

@AutoService(ProjectorPlugin.class)
public class SpringTxProjectorPlugin implements ProjectorPlugin {

  @Nullable
  @Override
  public Collection<ProjectorLens> lensFor(@NonNull Projection p) {
    if (p instanceof SpringTxProjection) {

      SpringTransactional transactional = p.getClass().getAnnotation(SpringTransactional.class);

      if (transactional == null) {
        throw new IllegalStateException(
            "SpringTxProjection must be annotated with @"
                + SpringTransactional.class.getSimpleName()
                + ". Offending class:"
                + p.getClass().getName());
      }

      SpringTxProjection jdbcProjection = (SpringTxProjection) p;

      return Collections.singletonList(new SpringTransactionalLens(jdbcProjection));
    }

    // any other case:
    return Collections.emptyList();
  }
}
