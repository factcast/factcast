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
package org.factcast.factus.projection.parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
// TODO
class HandlerParameterTransformerTest {
  //
  //    final ProjectorContext dummyCtx = new ProjectorContext() {
  //    };
  //    final Fact dummyFact = Fact.builder().buildWithoutPayload();
  //    final ZonedDateTime expectedDate = ZonedDateTime.now();
  //    final ZonedDateTime birthDay = ZonedDateTime.of(2022, 2, 2, 0, 0, 0, 0,
  // ZoneId.systemDefault());
  //
  //    @Nested
  //    class WhenForCalling {
  //
  //        @BeforeEach
  //        void setup() {
  //        }
  //
  //
  //        class NowContributor implements HandlerParameterContributor {
  //            @Nullable
  //            @Override
  //            public HandlerParameterProvider providerFor(@NonNull Class<?> type, @NonNull
  // Set<Annotation> annotations) {
  //                if (ZonedDateTime.class.equals(type) && annotations.isEmpty()) {
  //                    return (fact, projectorContext) -> expectedDate;
  //                }
  //                return null;
  //            }
  //        }
  //
  //        class FixedDateContributor implements HandlerParameterContributor {
  //            @Nullable
  //            @Override
  //            public HandlerParameterProvider providerFor(@NonNull Class<?> type, @NonNull
  // Set<Annotation> annotations) {
  //                if (ZonedDateTime.class.equals(type) && annotations.isEmpty()) {
  //                    return (fact, projectorContext) -> ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0,
  // ZoneId.systemDefault());
  //                }
  //                return null;
  //            }
  //        }
  //
  //        class BirthdayContributor implements HandlerParameterContributor {
  //            @Nullable
  //            @Override
  //            public HandlerParameterProvider providerFor(@NonNull Class<?> type, @NonNull
  // Set<Annotation> annotations) {
  //                if (ZonedDateTime.class.equals(type) &&
  // annotations.stream().anyMatch(BirthDay.class::isInstance)) {
  //                    return (fact, projectorContext) -> {
  //                        return birthDay;
  //                    };
  //                }
  //                return null;
  //            }
  //        }
  //
  //
  //        class NopContributor implements HandlerParameterContributor {
  //
  //            @Nullable
  //            @Override
  //            public HandlerParameterProvider providerFor(@NonNull Class<?> type, @NonNull
  // Set<Annotation> annotations) {
  //                return null;
  //            }
  //        }
  //
  //        @SneakyThrows
  //        @Test
  //        void picksContributor() {
  //            Method method = TestInterface1.class.getMethod("foo", ZonedDateTime.class);
  //            List<HandlerParameterContributor> contributors = Collections.singletonList(new
  // NowContributor());
  //            HandlerParameterTransformer t = HandlerParameterTransformer.forCalling(method,
  // contributors);
  //            Assertions.assertThat(t.apply(dummyFact,
  // dummyCtx)).hasSize(1).contains(expectedDate);
  //        }
  //
  //        @SneakyThrows
  //        @Test
  //        void picksContributorByAnnotation() {
  //            Method method = TestInterface1.class.getMethod("bar", ZonedDateTime.class);
  //            List<HandlerParameterContributor> contributors = Lists.newArrayList(new
  // NopContributor(), new NowContributor(), new BirthdayContributor());
  //            HandlerParameterTransformer t = HandlerParameterTransformer.forCalling(method,
  // contributors);
  //            Assertions.assertThat(t.apply(dummyFact, dummyCtx)).hasSize(1).contains(birthDay);
  //        }
  //
  //        @SneakyThrows
  //        @Test
  //        void picksFirstMatchingContributor() {
  //            Method method = TestInterface1.class.getMethod("foo", ZonedDateTime.class);
  //            List<HandlerParameterContributor> contributors = Lists.newArrayList(new
  // NopContributor(), new NowContributor(), new FixedDateContributor());
  //            HandlerParameterTransformer t = HandlerParameterTransformer.forCalling(method,
  // contributors);
  //            Assertions.assertThat(t.apply(dummyFact,
  // dummyCtx)).hasSize(1).contains(expectedDate);
  //        }
  //
  //        @SneakyThrows
  //        @Test
  //        void failsOnContributorNotFound() {
  //            Method method = TestInterface1.class.getMethod("foo", ZonedDateTime.class);
  //            List<HandlerParameterContributor> c = Collections.emptyList();
  //            Assertions.assertThatThrownBy(() -> {
  //                HandlerParameterTransformer.forCalling(method, c);
  //            }).isInstanceOf(IllegalArgumentException.class);
  //        }
  //
  //        @SneakyThrows
  //        @Test
  //        void failsOnMatchingContributorNotFound() {
  //            Method method = TestInterface1.class.getMethod("foo", ZonedDateTime.class);
  //            List<HandlerParameterContributor> c = Collections.singletonList(new
  // NopContributor());
  //            Assertions.assertThatThrownBy(() -> HandlerParameterTransformer.forCalling(method,
  // c)).isInstanceOf(IllegalArgumentException.class);
  //        }
  //    }

}

interface TestInterface1 {
  void foo(ZonedDateTime now);

  void bar(@BirthDay ZonedDateTime now);
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface BirthDay {}
