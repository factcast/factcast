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

import java.util.Iterator;
import java.util.ServiceLoader;

import org.factcast.core.Fact;
import org.factcast.factus.event.Specification;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
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

    public static FactSpecCoordinates from(Class<?> clazz) {

        String defaultType = clazz.getSimpleName();
        String defaultNs = "default";

        val spec = clazz.getAnnotation(Specification.class);
        if (spec == null) {
            throw new IllegalArgumentException("@" + Specification.class.getSimpleName()
                    + " missing on " + clazz);
        }

        String _ns = spec.ns();
        if (_ns.trim().isEmpty()) {
            _ns = defaultNs;
        }

        if (_ns.endsWith("$")) {
            _ns = _ns + suffixProvider().get();
        }

        String _type = spec.type();
        if (_type.trim().isEmpty()) {
            _type = defaultType;
        }

        int version = spec.version();

        return new FactSpecCoordinates(_ns, _type, version);
    }

    private static DynamicNamespaceSuffixProvider cachedSuffixProvider = null;

    private static DynamicNamespaceSuffixProvider suffixProvider() {
        if (cachedSuffixProvider == null) {
            cachedSuffixProvider = discoverSuffixProvider();
        }
        return cachedSuffixProvider;
    }

    @SuppressWarnings("unchecked")
    private static DynamicNamespaceSuffixProvider discoverSuffixProvider() {
        ServiceLoader<DynamicNamespaceSuffixProvider> sl = ServiceLoader.load(
                DynamicNamespaceSuffixProvider.class);
        Iterator<DynamicNamespaceSuffixProvider> iterator = sl.iterator();
        if (iterator.hasNext())
            return iterator.next();
        else
            return () -> {
                throw new IllegalArgumentException(
                        "$ replacements in namespaces should only be used in tests. If this is a test, please fix this by adding a dependency to org.factcast:factcast-test.");
            };
    }

}
