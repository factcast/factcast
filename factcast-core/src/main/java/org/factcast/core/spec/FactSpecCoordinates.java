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
package org.factcast.core.spec;

import org.factcast.core.Fact;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FactSpecCoordinates {

    String ns;

    String type;

    int version;

    public static FactSpecCoordinates from(@NonNull FactSpec fs) {
        return new FactSpecCoordinates(fs.ns(), fs.type(), fs.version());
    }

    public static FactSpecCoordinates from(@NonNull Fact fact) {
        return new FactSpecCoordinates(fact.ns(), fact.type(), fact.version());
    }
}
