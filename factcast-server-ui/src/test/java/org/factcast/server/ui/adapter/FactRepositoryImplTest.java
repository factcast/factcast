/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.adapter;

import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.store.LocalFactStore;
import org.factcast.server.ui.config.SecurityService;
import org.factcast.server.ui.full.FullQueryBean;
import org.factcast.server.ui.id.IdQueryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FactRepositoryImplTest {

  @Mock private LocalFactStore fs;
  @Mock private SecurityService securityService;
  @InjectMocks private FactRepositoryImpl underTest;

  @Nested
  class WhenFindingBy {

    @BeforeEach
    void setup() {}

    @Test
    void returnsEmptyOnNull() {
      Assertions.assertThat(underTest.findBy(new IdQueryBean())).isEmpty();
    }

    @Test
    void passesParametersWithZeroVersion() {
      UUID id = UUID.randomUUID();
      IdQueryBean q = new IdQueryBean();
      q.setId(id);
      q.setVersion(null);
      Assertions.assertThat(underTest.findBy(q)).isEmpty();

      verify(fs).fetchByIdAndVersion(id, 0);
    }

    @Test
    void passesParametersWithNonZeroVersion() {
      UUID id = UUID.randomUUID();
      IdQueryBean q = new IdQueryBean();
      q.setId(id);
      q.setVersion(17);
      Assertions.assertThat(underTest.findBy(q)).isEmpty();

      verify(fs).fetchByIdAndVersion(id, 17);
    }

    @Test
    void passesResultIfCanRead() {
      UUID id = UUID.randomUUID();
      IdQueryBean q = new IdQueryBean();
      q.setId(id);
      q.setVersion(17);
      Fact fact = Fact.builder().buildWithoutPayload();
      when(securityService.canRead(fact)).thenReturn(true);
      when(fs.fetchByIdAndVersion(id, 17)).thenReturn(Optional.of(fact));

      Assertions.assertThat(underTest.findBy(q)).isNotEmpty();
    }

    @Test
    void filtersResultIfCannotRead() {
      UUID id = UUID.randomUUID();
      IdQueryBean q = new IdQueryBean();
      q.setId(id);
      q.setVersion(17);
      Fact fact = Fact.builder().buildWithoutPayload();
      when(securityService.canRead(fact)).thenReturn(false);
      when(fs.fetchByIdAndVersion(id, 17)).thenReturn(Optional.of(fact));

      Assertions.assertThat(underTest.findBy(q)).isEmpty();
    }
  }

  @Nested
  class WhenNamespacesing {

    @BeforeEach
    void setup() {}

    @Test
    void sorts() {
      when(securityService.canRead(any(String.class))).thenReturn(true);
      when(fs.enumerateNamespaces()).thenReturn(Set.of("z", "x", "y"));
      Assertions.assertThat(underTest.namespaces(null)).containsExactly("x", "y", "z");
    }

    @Test
    void filtersIfCannotRead() {
      when(securityService.canRead(any(String.class)))
          .thenAnswer(a -> a.getArgument(0).equals("z"));
      when(fs.enumerateNamespaces()).thenReturn(Set.of("z", "x", "y"));
      Assertions.assertThat(underTest.namespaces(null)).containsExactly("z");
    }

    @Test
    void filtersByInput() {
      when(securityService.canRead(any(String.class))).thenReturn(true);
      when(fs.enumerateNamespaces()).thenReturn(Set.of("aaa", "abb", "aab", "bab"));
      Assertions.assertThat(underTest.namespaces("ab")).containsExactly("aab", "abb", "bab");
    }

    @Test
    void filtersByInputAndPermission() {
      when(securityService.canRead(any(String.class)))
          .thenAnswer(a -> a.getArgument(0).toString().contains("z"));

      when(fs.enumerateNamespaces()).thenReturn(Set.of("aaaz", "abb", "aabz", "bab"));
      Assertions.assertThat(underTest.namespaces("ab")).containsExactly("aabz");
    }
  }

  @Nested
  class WhenTypesing {
    private final String NS = "ns";

    @BeforeEach
    void setup() {}

    @Test
    void emptyOnLackingPermissions() {
      when(securityService.canRead(NS)).thenReturn(false);
      Assertions.assertThat(underTest.types(NS, null)).isEmpty();
      Assertions.assertThat(underTest.types(NS, "dlskfhsdk")).isEmpty();
    }

    @Test
    void sorts() {
      when(securityService.canRead(NS)).thenReturn(true);
      when(fs.enumerateTypes(NS)).thenReturn(Set.of("t3", "t1", "t2"));
      Assertions.assertThat(underTest.types(NS, null)).containsExactly("t1", "t2", "t3");
    }

    @Test
    void filtersByInput() {
      when(securityService.canRead(NS)).thenReturn(true);
      when(fs.enumerateTypes(NS)).thenReturn(Set.of("t3", "t1z", "t2z"));
      Assertions.assertThat(underTest.types(NS, "z")).containsExactly("t1z", "t2z");
    }
  }

  @Nested
  class WhenLatestingSerial {
    @Test
    void passesResponse() {
      when(fs.latestSerial()).thenReturn(72L);
      Assertions.assertThat(underTest.latestSerial()).isEqualTo(72L);
    }
  }

  @Nested
  class WhenFindingIdOfSerial {
    UUID ID = UUID.randomUUID();

    @Test
    void passesResponse() {
      when(fs.fetchBySerial(123))
          .thenReturn(Optional.of(Fact.builder().id(ID).buildWithoutPayload()));
      Assertions.assertThat(underTest.findIdOfSerial(123)).isNotNull().isNotEmpty().hasValue(ID);
    }
  }

  @Nested
  class WhenFetchingChunk {
    @Mock private FullQueryBean bean;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenLastingSerialBefore {
    @Test
    void passesParamAndResponse() {
      LocalDate d = LocalDate.now();
      when(fs.lastSerialBefore(d)).thenReturn(72L);
      Assertions.assertThat(underTest.lastSerialBefore(d)).isNotEmpty().hasValue(72L);
    }
  }
}
