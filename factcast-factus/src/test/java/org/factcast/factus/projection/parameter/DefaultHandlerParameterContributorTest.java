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
package org.factcast.factus.projection.parameter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.*;
import org.factcast.factus.Meta;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.*;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"ALL", "java:S1186"})
class DefaultHandlerParameterContributorTest {
  @Test
  void providesFSP() {
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(FactStreamPosition.class, null, new HashSet<>());

    Fact fact = new TestFact();
    FactStreamPosition fsp = FactStreamPosition.from(fact);
    TestProjection p = mock(TestProjection.class);
    when(p.factStreamPosition()).thenReturn(fsp);

    Assertions.assertThat(provider.apply(mock(EventSerializer.class), fact, p)).isEqualTo(fsp);
  }

  @Test
  void providesStringMeta() {
    class MetaString {
      public void apply(@Nullable @Meta("narf") String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat(provider.apply(mock(EventSerializer.class), fact, p)).isEqualTo("poit");
  }

  @SuppressWarnings("unchecked")
  @Test
  void providesOptionalMeta() {
    class MetaString {
      public void apply(@Meta("narf") Optional<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((Optional) provider.apply(mock(EventSerializer.class), fact, p))
        .hasValue("poit");
  }

  @Test
  void providesListMeta() {
    class MetaString {
      public void apply(@Meta("narf") List<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((List) provider.apply(mock(EventSerializer.class), fact, p))
        .containsExactly("poit");
  }

  @Test
  void providesIterableMeta() {
    class MetaString {
      public void apply(@Meta("narf") Iterable<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((List) provider.apply(mock(EventSerializer.class), fact, p))
        .containsExactly("poit");
  }

  @Test
  void providesListMetaMultiValue() {
    class MetaString {
      public void apply(@Meta("narf") List<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact =
        Fact.builder().addMeta("narf", "poit").addMeta("narf", "zort").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((List) provider.apply(mock(EventSerializer.class), fact, p))
        .containsExactly("poit", "zort");
  }

  @Test
  void providesEmptyOptionalMeta() {
    class MetaString {
      public void apply(@Meta("narf") Optional<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("no-exactly-narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((Optional) provider.apply(mock(EventSerializer.class), fact, p))
        .isEmpty();
  }

  @Test
  void providesEmptyListMeta() {
    class MetaString {
      public void apply(@Meta("narf") List<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("no-exactly-narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((List) provider.apply(mock(EventSerializer.class), fact, p)).isEmpty();
  }

  @Test
  void providesNullForStringMeta() {
    class MetaString {
      public void apply(@Nullable @Meta("narf") String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("no-exactly-narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat(provider.apply(mock(EventSerializer.class), fact, p)).isNull();
  }

  @Test
  void rejectsRawCollection() {
    //noinspection rawtypes
    class MetaString {
      public void apply(@Meta("narf") List narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsRawOptional() {
    //noinspection rawtypes
    class MetaString {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      public void apply(@Meta("narf") Optional narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void providesList() {
    //noinspection rawtypes
    class MetaList extends TestProjection {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      public void apply(@Meta("narf") List<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaList.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();

    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact =
        Fact.builder().addMeta("narf", "poit").addMeta("narf", "zort").buildWithoutPayload();
    MetaList p = mock(MetaList.class);
    Assertions.assertThat((List) (provider.apply(mock(EventSerializer.class), fact, p)))
        .containsExactly("poit", "zort");
  }

  @Test
  void providesEmptyList() {
    //noinspection rawtypes
    class MetaList extends TestProjection {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      public void apply(@Meta("narf") List<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaList.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();

    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().buildWithoutPayload();
    MetaList p = mock(MetaList.class);
    Assertions.assertThat((List) (provider.apply(mock(EventSerializer.class), fact, p))).isEmpty();
  }

  @Test
  void rejectsEmptyName() {
    //noinspection rawtypes
    class MetaString {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      public void apply(@Meta("") @Nullable String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonStringOptional() {
    class MetaString {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      public void apply(@Meta("narf") Optional<Integer> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsStringIfNotNullable() {
    class MetaString {
      public void apply(@Meta("narf") String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonStringType() {
    class MetaString {
      public void apply(@Meta("narf") Integer narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonStringCollectionType() {
    class MetaString {
      public void apply(@Meta("narf") Iterable<Integer> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> "apply".equals(me.getName()))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest = new DefaultHandlerParameterContributor();
    Class<?> type = m.getParameterTypes()[0];
    Type genericType = m.getGenericParameterTypes()[0];
    HashSet<Annotation> annotations = Sets.newHashSet(m.getParameterAnnotations()[0]);
    assertThatThrownBy(
            () -> {
              undertest.providerFor(type, genericType, annotations);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  static class TestProjection implements Projection, FactStreamPositionAware {
    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return null;
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}
  }
}
