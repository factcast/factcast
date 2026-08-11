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

class RevisionToRevisionIdRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new RevisionToRevisionIdRecipe())
        .parser(
            JavaParser.fromJavaVersion().classpath("lombok", "factcast-core", "factcast-factus"));
  }

  @Test
  void migratesRevision() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 1)
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revisionId = "1")
            class MyProjection {}
            """));
  }

  @Test
  void migratesRevisionKeepingName() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(name = "foo", revision = 1)
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(name = "foo", revisionId = "1")
            class MyProjection {}
            """));
  }

  @Test
  void migratesRevisionInAnyPosition() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 1, name = "foo")
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revisionId = "1", name = "foo")
            class MyProjection {}
            """));
  }

  @Test
  void migratesLongLiteral() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 1L)
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revisionId = "1")
            class MyProjection {}
            """));
  }

  @Test
  void migratesZeroRevision() {
    // revision = 0 was never used for the scoped name (ScopedName requires revision() > 0), and
    // revisionId defaults to "0", so "0" is exactly the key that was persisted before.
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 0)
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revisionId = "0")
            class MyProjection {}
            """));
  }

  @Test
  void migratesNestedClasses() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            class Outer {
                @ProjectionMetaData(revision = 3)
                static class Inner {}

                @ProjectionMetaData(name = "inner", revision = 43)
                class NonStaticInner {}
            }
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            class Outer {
                @ProjectionMetaData(revisionId = "3")
                static class Inner {}

                @ProjectionMetaData(name = "inner", revisionId = "43")
                class NonStaticInner {}
            }
            """));
  }

  @Test
  void normalizesNonDecimalLiterals() {
    // the parsed literal value, not its source, decides the revisionId, so that whatever notation
    // was used ends up as the very same string String.valueOf(long) produced before
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            class Outer {
                @ProjectionMetaData(revision = 0x10)
                static class Hex {}

                @ProjectionMetaData(revision = 1_000)
                static class Underscores {}
            }
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            class Outer {
                @ProjectionMetaData(revisionId = "16")
                static class Hex {}

                @ProjectionMetaData(revisionId = "1000")
                static class Underscores {}
            }
            """));
  }

  @Test
  void preservesUnusualFormatting() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData( name = "foo" ,  revision  =  1 )
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData( name = "foo" ,  revisionId  =  "1" )
            class MyProjection {}
            """));
  }

  @Test
  void doesNotChangeAlreadyMigratedAnnotation() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revisionId = "1")
            class MyProjection {}
            """));
  }

  @Test
  void doesNotChangeAnnotationWithBothAttributes() {
    // rewriting would produce a duplicate revisionId, picking a winner is a human decision
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 2, revisionId = "3")
            class MyProjection {}
            """));
  }

  @Test
  void doesNotChangeNegativeRevision() {
    // a negative revision never reached the scoped name, so "-1" would change the persisted key
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = -1)
            class MyProjection {}
            """));
  }

  @Test
  void doesNotChangeConstantReference() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            class Outer {
                static final long REVISION = 1L;

                @ProjectionMetaData(revision = REVISION)
                static class MyProjection {}
            }
            """));
  }

  @Test
  void doesNotChangeComputedRevision() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 1 + 1)
            class MyProjection {}
            """));
  }

  @Test
  void doesNotChangeAnnotationWithoutArguments() {
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            class Outer {
                @ProjectionMetaData
                static class NoParentheses {}

                @ProjectionMetaData()
                static class EmptyParentheses {}
            }
            """));
  }

  @Test
  void doesNotChangeOtherAnnotationWithRevisionAttribute() {
    // ProjectionMetaData is used in the same file on purpose, so this asserts the annotation
    // matcher and not just the UsesType precondition
    rewriteRun(
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @interface SomethingElse {
                long revision() default 0;
            }

            class Outer {
                @ProjectionMetaData(revisionId = "1")
                static class Migrated {}

                @SomethingElse(revision = 1)
                static class NotAProjectionMetaData {}
            }
            """));
  }

  @Test
  void declarativeRecipeIsRegistered() {
    rewriteRun(
        spec -> spec.recipeFromResources("org.factcast.factus.migration.RevisionToRevisionId"),
        java(
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revision = 1)
            class MyProjection {}
            """,
            """
            import org.factcast.factus.serializer.ProjectionMetaData;

            @ProjectionMetaData(revisionId = "1")
            class MyProjection {}
            """));
  }
}
