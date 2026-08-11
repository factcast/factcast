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

import static org.openrewrite.Tree.randomId;

import java.util.List;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

@EqualsAndHashCode(callSuper = false)
public class RevisionToRevisionIdRecipe extends Recipe {

  private static final String PROJECTION_META_DATA =
      "org.factcast.factus.serializer.ProjectionMetaData";

  @Override
  public @NonNull String getDisplayName() {
    return "Migrate @ProjectionMetaData revision to revisionId";
  }

  @Override
  public @NonNull String getDescription() {
    return "Replaces the deprecated numeric revision attribute of @ProjectionMetaData with an "
        + "equivalent revisionId string attribute. Only non-negative integer literals are "
        + "rewritten, because only for those the resulting scoped (and therefore persisted) "
        + "projection name stays byte-identical.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    // cheap precondition only, the visitor needs type attribution anyway
    return Preconditions.check(
        new UsesType<>(PROJECTION_META_DATA, false), new RevisionToRevisionIdVisitor());
  }

  private static class RevisionToRevisionIdVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final AnnotationMatcher PROJECTION_META_DATA_MATCHER =
        new AnnotationMatcher("@" + PROJECTION_META_DATA);

    private static final String OLD_ATTRIBUTE = "revision";
    private static final String NEW_ATTRIBUTE = "revisionId";

    @Override
    public J.@NonNull Annotation visitAnnotation(
        J.@NonNull Annotation annotation, ExecutionContext ctx) {
      J.Annotation a = super.visitAnnotation(annotation, ctx);

      if (a.getArguments() == null
          || !PROJECTION_META_DATA_MATCHER.matches(a)
          // both attributes at once is rejected by ProjectionMetaData.Resolver#validate, and
          // adding a second revisionId would not even compile
          || hasAttribute(a, NEW_ATTRIBUTE)) {
        return a;
      }

      return a.withArguments(
          ListUtils.map(a.getArguments(), RevisionToRevisionIdVisitor::migrateRevision));
    }

    private boolean requiresRefactoring(J.Annotation annotation) {
      return annotation.getArguments() != null
          || PROJECTION_META_DATA_MATCHER.matches(annotation)
          // both attributes at once is rejected by ProjectionMetaData.Resolver#validate, and
          // adding a second revisionId would not even compile
          || !hasAttribute(annotation, NEW_ATTRIBUTE);
    }

    private static Expression migrateRevision(Expression arg) {
      J.Assignment revision = asAttribute(arg, OLD_ATTRIBUTE);
      if (revision == null) {
        return arg;
      }

      String revisionId = revisionIdFor(revision.getAssignment());
      if (revisionId == null) {
        return arg;
      }

      J.Identifier attributeName = (J.Identifier) revision.getVariable();
      return revision
          // fieldType is null for annotation attributes, but a stale one would make the name of
          // the identifier and the name of its type information disagree
          .withVariable(attributeName.withSimpleName(NEW_ATTRIBUTE).withFieldType(null))
          .withAssignment(stringLiteral(revisionId, revision.getAssignment()))
          .withType(JavaType.Primitive.String);
    }

    /** Keeps the whitespace (and markers) of the expression it replaces. */
    private static J.Literal stringLiteral(String value, Expression replaced) {
      return new J.Literal(
          randomId(),
          replaced.getPrefix(),
          replaced.getMarkers(),
          value,
          // valueSource is printed verbatim, so the quotes belong here and not in value
          "\"" + value + "\"",
          null,
          JavaType.Primitive.String);
    }

    /**
     * @return the decimal string ScopedName used to build the persisted projection key from the
     *     given old revision value, or null if it must not be migrated.
     */
    @Nullable
    private static String revisionIdFor(Expression value) {
      // constant references, arithmetic and J.Unary (e.g. -0x1) cannot be turned into a string
      // constant without either resolving constants or changing semantics
      if (!(value instanceof J.Literal literal)) {
        return null;
      }
      Object literalValue = literal.getValue();
      // the parser normalizes the value, so 1, 1L, 0x1 and 1_000 all arrive as Integer/Long and
      // collapse into the very same decimal string
      if (!(literalValue instanceof Integer) && !(literalValue instanceof Long)) {
        return null;
      }
      long revision = ((Number) literalValue).longValue();
      // ScopedName#revisionIdentifier only uses revision() if it is > 0, and falls back to
      // revisionId() (defaulting to DEFAULT_REVISION_ID "0") otherwise. That makes 0 safe to
      // migrate to "0", but a negative revision would change the persisted projection key.
      return revision < 0 ? null : String.valueOf(revision);
    }

    private static boolean hasAttribute(J.Annotation a, String attributeName) {
      List<Expression> arguments = a.getArguments();
      if (arguments == null) {
        return false;
      }
      for (Expression argument : arguments) {
        if (asAttribute(argument, attributeName) != null) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    private static J.Assignment asAttribute(Expression arg, String attributeName) {
      if (arg instanceof J.Assignment assignment
          && assignment.getVariable() instanceof J.Identifier variable
          && attributeName.equals(variable.getSimpleName())) {
        return assignment;
      }
      return null;
    }
  }
}
