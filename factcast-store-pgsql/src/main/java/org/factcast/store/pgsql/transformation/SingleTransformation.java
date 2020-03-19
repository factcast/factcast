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
package org.factcast.store.pgsql.transformation;

import lombok.NonNull;
import lombok.Value;

@Value
public class SingleTransformation implements Transformation {
    @NonNull
    TransformationKey key;

    boolean isSynthetic;

    int fromVersion;

    int toVersion;

    // can be null?
    String transformationCode;

    public static SingleTransformation of(@NonNull TransformationSource source,
            String transformation) {
        return new SingleTransformation(source.toKey(), source.isSynthetic(), source.from(), source
                .to(),
                transformation);
    }
}
