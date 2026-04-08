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
package org.factcast.factus.migration;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class PostprocessToCollectionRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new PostprocessToCollectionRecipe())
        .parser(
            JavaParser.fromJavaVersion()
                .classpath("lombok", "factcast-core", "factcast-factus"));
  }

  @Test
  void changesListParamAndReturnType() {
    rewriteRun(
        java(
            """
            import java.util.List;
            import org.factcast.core.spec.FactSpec;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public List<FactSpec> postprocess(List<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """,
            // List import is kept because OpenRewrite's maybeRemoveImport does not
            // remove it when it still has it loaded on memory
            """
            import java.util.Collection;
            import java.util.List;
            import org.factcast.core.spec.FactSpec;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public Collection<FactSpec> postprocess(Collection<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """));
  }

  @Test
  void changesAnnotatedReturnType() {
    rewriteRun(
        java(
            """
            import java.util.List;
            import lombok.NonNull;
            import org.factcast.core.spec.FactSpec;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public @NonNull List<FactSpec> postprocess(@NonNull List<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """,
            // List import is kept because OpenRewrite's maybeRemoveImport does not
            // remove it when it still has it loaded on memory
            """
            import java.util.Collection;
            import java.util.List;
            import lombok.NonNull;
            import org.factcast.core.spec.FactSpec;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public @NonNull Collection<FactSpec> postprocess(@NonNull Collection<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """));
  }

  @Test
  void doesNotChangeOtherMethodNames() {
    rewriteRun(
        java(
            """
            import java.util.List;
            import org.factcast.core.spec.FactSpec;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public List<FactSpec> configure(List<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """));
  }

  @Test
  void doesNotChangeAlreadyMigratedMethod() {
    rewriteRun(
        java(
            """
            import java.util.Collection;
            import org.factcast.core.spec.FactSpec;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public Collection<FactSpec> postprocess(Collection<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """));
  }

  @Test
  void doesNotChangeNonProjectionClass() {
    rewriteRun(
        java(
            """
            import java.util.List;
            import org.factcast.core.spec.FactSpec;

            class NotAProjection {
                public List<FactSpec> postprocess(List<FactSpec> specsAsDiscovered) {
                    return specsAsDiscovered;
                }
            }
            """));
  }

  @Test
  void doesNotChangeWrongTypeParam() {
    rewriteRun(
        java(
            """
            import java.util.List;
            import org.factcast.factus.projection.Projection;

            class MyProjection implements Projection {
                public List<String> postprocess(List<String> items) {
                    return items;
                }
            }
            """));
  }
}
