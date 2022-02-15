/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
