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
package org.factcast.store.internal.pipeline;

import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;

// TODO check, if it can just be replaced by the buffered variant
@Slf4j
public class TransformingFactPipeline extends AbstractFactPipeline {
  private final FactTransformerService service;
  private final FactTransformers transformers;

  public TransformingFactPipeline(
      @NonNull FactPipeline parent,
      @NonNull FactTransformerService service,
      @NonNull FactTransformers transformers) {
    super(parent);
    this.service = service;
    this.transformers = transformers;
  }

  @Override
  public void fact(@Nullable Fact f) {
    log.trace("processing {}", f);
    if (f == null) parent.fact(f);
    else {
      TransformationRequest transformationRequest = transformers.prepareTransformation(f);
      if (transformationRequest == null) {
        // pass unmodified
        parent.fact(f);
      } else {
        // transform and pass
        parent.fact(service.transform(transformationRequest));
      }
    }
  }
}
