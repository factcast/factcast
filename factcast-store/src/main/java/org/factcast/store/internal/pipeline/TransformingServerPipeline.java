/*
 * Copyright © 2017-2024 factcast.org
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

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;

public class TransformingServerPipeline extends AbstractServerPipeline {

  private final FactTransformerService service;
  private final FactTransformers transformers;

  public TransformingServerPipeline(
      @NonNull ServerPipeline parent,
      @NonNull FactTransformerService service,
      @NonNull FactTransformers transformers) {
    super(parent);
    this.service = service;
    this.transformers = transformers;
  }

  @Override
  public void process(@NonNull Signal s) {

    if (s instanceof Signal.FactSignal fs) {

      Fact f = fs.fact();
      TransformationRequest transformationRequest = transformers.prepareTransformation(f);
      if (transformationRequest == null) {
        // pass unmodified
        parent.process(s);
      } else {
        // transform and pass
        parent.fact(service.transform(transformationRequest));
      }
    } else parent.process(s);
  }
}
  //        @Override
  //        public void fact(@Nullable Fact f) {
  //            else {
  //                TransformationRequest transformationRequest =
  // transformers.prepareTransformation(f);
  //                if (transformationRequest == null) {
  //                    // pass unmodified
  //                    parent.fact(f);
  //                } else {
  //                    // transform and pass
  //                    parent.fact(service.transform(transformationRequest));
  //                }
  //            }
  //        }
