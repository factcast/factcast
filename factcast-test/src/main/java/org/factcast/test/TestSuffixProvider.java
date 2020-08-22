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
package org.factcast.test;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.spec.DynamicNamespaceSuffixProvider;

/**
 * This is meant for integration testing only
 */
public class TestSuffixProvider implements DynamicNamespaceSuffixProvider {

    private static final AtomicLong state = new AtomicLong();

    private static final String INITIAL = null;

    private static String current = INITIAL;

    public String get() {
        if (current == INITIAL)
            throw new IllegalStateException(
                    "In order to make use of automatic namespace assignment in tests, \n   you need to either activate JUnit5 extension detection by adding\n   '-Djunit.jupiter.extensions.autodetection.enabled=true' as a VM Parameter\n   or annotate your test with\n   '@ExtendWith(FactCastNamespaceExtension.class)'");

        return current;
    }

    public static void next() {
        current = nextValue();
    }

    private static String nextValue() {
        return String.format("%08x", state.incrementAndGet());
    }
}
