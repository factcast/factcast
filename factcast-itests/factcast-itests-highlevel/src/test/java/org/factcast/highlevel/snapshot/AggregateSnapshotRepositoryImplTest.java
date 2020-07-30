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
package org.factcast.highlevel.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.checkerframework.checker.units.qual.A;
import org.factcast.core.snap.SnapshotRepository;
import org.factcast.highlevel.projection.Aggregate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.NonNull;

@ExtendWith(MockitoExtension.class)
class AggregateSnapshotRepositoryImplTest {

    @Mock
    private SnapshotRepository snap;

    @InjectMocks
    private AggregateSnapshotRepositoryImpl underTest;

    @Nested
    class WhenFindingLatest {
        private @NonNull Class<A> type;

        @Mock
        private @NonNull UUID aggregateId;

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenGettingSerialVersionUid {

        @Test
        void retrievesExistingSVUID() {
            assertEquals(42, underTest.getSerialVersionUid(WithSVUID.class));
            assertEquals(0, underTest.getSerialVersionUid(WithoutSVUID.class));
        }
    }

    @Nested
    class WhenCreatingKey {

        @Test
        void createsKeyIncludingSerialVersionUid() {
            String with = underTest.createKeyForType(WithSVUID.class);
            String without = underTest.createKeyForType(WithoutSVUID.class);

            assertThat(with).contains(WithSVUID.class.getCanonicalName()).contains(":42");
            assertThat(without).contains(WithoutSVUID.class.getCanonicalName()).contains(":0");

        }
    }

    public static class WithSVUID extends Aggregate {
        private static final long serialVersionUID = 42L;
    }

    public static class WithoutSVUID extends Aggregate {
    }

}
