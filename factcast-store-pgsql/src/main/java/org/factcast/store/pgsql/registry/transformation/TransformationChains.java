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
package org.factcast.store.pgsql.registry.transformation;

import java.util.LinkedList;
import java.util.List;

import org.factcast.store.pgsql.registry.SchemaRegistry;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransformationChains {

    final SchemaRegistry r;

    public TransformationChain build(TransformationKey key, int from, int to) {
        List<Transformation> list = new LinkedList<>();

        // r.get transformations

        return null;// FIXME
    }

}
