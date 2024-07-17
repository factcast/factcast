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
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFact;
import org.factcast.factus.Meta;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Projection;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"ALL", "java:S1186"})
class DefaultHandlerParameterContributorTest {
  @Test
  void providesFSP() {
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
    HandlerParameterProvider provider =
        undertest.providerFor(FactStreamPosition.class, null, new HashSet<>());

    Fact fact = new TestFact();
    FactStreamPosition fsp = FactStreamPosition.from(fact);
    TestProjection p = mock(TestProjection.class);
    when(p.factStreamPosition()).thenReturn(fsp);

    Assertions.assertThat(provider.apply(fact, p)).isEqualTo(fsp);
  }

  @Test
  void providesStringMeta() {
    class MetaString {
      public void apply(@Nullable @Meta(key = "narf") String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat(provider.apply(fact, p)).isEqualTo("poit");
  }

  @SuppressWarnings("unchecked")
  @Test
  void providesOptionalMeta() {
    class MetaString {
      public void apply(@Meta(key = "narf") Optional<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((Optional) provider.apply(fact, p)).hasValue("poit");
  }

  @Test
  void providesEmptyOptionalMeta() {
    class MetaString {
      public void apply(@Meta(key = "narf") Optional<String> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("no-exactly-narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat(((Optional) provider.apply(fact, p))).isEmpty();
  }

  @Test
  void providesNullForStringMeta() {
    class MetaString {
      public void apply(@Nullable @Meta(key = "narf") String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
    HandlerParameterProvider provider =
        undertest.providerFor(
            m.getParameterTypes()[0],
            m.getGenericParameterTypes()[0],
            Sets.newHashSet(m.getParameterAnnotations()[0]));
    Assertions.assertThat(provider).isNotNull();
    Fact fact = Fact.builder().meta("no-exactly-narf", "poit").buildWithoutPayload();
    TestProjection p = mock(TestProjection.class);
    Assertions.assertThat((provider.apply(fact, p))).isNull();
  }

  @Test
  void rejectsRawOptional() {
    //noinspection rawtypes
    class MetaString {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      public void apply(@Meta(key = "narf") Optional narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
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
      public void apply(@Meta(key = "narf") Optional<Integer> narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
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
      public void apply(@Meta(key = "narf") String narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
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
      public void apply(@Meta(key = "narf") Integer narf) {}
    }

    Method m =
        Arrays.stream(MetaString.class.getMethods())
            .filter(me -> me.getName().equals("apply"))
            .findFirst()
            .get();
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
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
