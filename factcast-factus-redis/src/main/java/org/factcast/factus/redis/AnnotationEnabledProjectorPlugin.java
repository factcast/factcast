package org.factcast.factus.redis;

import java.lang.annotation.Annotation;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;

@RequiredArgsConstructor
public abstract class AnnotationEnabledProjectorPlugin<A extends Annotation>
    implements ProjectorPlugin {

  private final Class<A> annotationClass;

  @Nullable
  @Override
  public final ProjectorLens lensFor(Projection p) {
    val a = p.getClass().getAnnotation(annotationClass);
    if (a == null) {
      return null;
    } else {
      return createLens(a, p);
    }
  }

  protected abstract ProjectorLens createLens(A a, Projection p);
}
