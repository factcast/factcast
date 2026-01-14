/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.transformation.chains;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import org.factcast.store.registry.transformation.Transformation;
import org.factcast.store.registry.transformation.TransformationKey;

@Value
public class TransformationChain implements Transformation {

  @NonNull String id;

  @NonNull TransformationKey key;

  int fromVersion;

  int toVersion;

  @ToString.Exclude @NonNull Optional<String> transformationCode;

  public static TransformationChain of(
      @NonNull TransformationKey key, @NonNull List<Transformation> orderedListOfSteps, String id) {

    Preconditions.checkArgument(!orderedListOfSteps.isEmpty());
    Preconditions.checkArgument(orderedListOfSteps.stream().allMatch(t -> key.equals(t.key())));

    int from = orderedListOfSteps.get(0).fromVersion();
    int to = orderedListOfSteps.get(orderedListOfSteps.size() - 1).toVersion();
    String compositeJson = createCompositeJS(orderedListOfSteps);

    return new TransformationChain(id, key, from, to, Optional.of(compositeJson));
  }

  private static String createCompositeJS(List<Transformation> list) {
    StringBuilder sb = new StringBuilder();
    sb.append("var steps = [");

    List<String> code =
        list.stream()
            .map(Transformation::transformationCode)
            .flatMap(Optional::stream)
            .collect(Collectors.toList());

    sb.append(String.join(",", code));
    sb.append("]; ");
    sb.append("function transform(event) { steps.forEach( function(f){f(event)} ); }");
    return sb.toString();
  }
}
