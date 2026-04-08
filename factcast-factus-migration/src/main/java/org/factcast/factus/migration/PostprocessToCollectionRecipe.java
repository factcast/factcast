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

import java.util.*;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.projection.Projection;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

@EqualsAndHashCode(callSuper = false)
public class PostprocessToCollectionRecipe extends Recipe {

  @Override
  public @NonNull String getDisplayName() {
    return "Migrate Projection.postprocess from List to Collection";
  }

  @Override
  public @NonNull String getDescription() {
    return "Changes postprocess(List<FactSpec>) overrides to postprocess(Collection<FactSpec>).";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new PostprocessVisitor();
  }

  private static class PostprocessVisitor extends JavaIsoVisitor<ExecutionContext> {
    @Override
    public J.@NonNull MethodDeclaration visitMethodDeclaration(
        J.@NonNull MethodDeclaration md, ExecutionContext ctx) {
      md = super.visitMethodDeclaration(md, ctx);

      if (shouldBeReplaced(md)) {
        J.VariableDeclarations param = (J.VariableDeclarations) md.getParameters().get(0);
        param = param.withTypeExpression(replaceListWithCollection(param.getTypeExpression()));
        md = md.withParameters(Collections.singletonList(param));
        md = md.withReturnTypeExpression(replaceListWithCollection(md.getReturnTypeExpression()));

        maybeAddImport(Collection.class.getName());
        maybeRemoveImport(List.class.getName());
      }

      return md;
    }

    private boolean shouldBeReplaced(J.MethodDeclaration md) {
      JavaType.FullyQualified param = getFirstParameterType(md);
      return (methodIsCalledPostProcess(md)
          && methodHasExactlyOneParameter(md)
          && methodDeclaredByProjection(md.getMethodType())
          && isTypeListOfFactSpec(param));
    }

    private static JavaType.FullyQualified getFirstParameterType(J.MethodDeclaration md) {
      // Use typeExpression.getType() directly: J.VariableDeclarations.getType() delegates to
      // the inner NamedVariable's type which is not updated when we replace typeExpression.
      TypeTree typeExpr = ((J.VariableDeclarations) md.getParameters().get(0)).getTypeExpression();
      JavaType type = typeExpr != null ? typeExpr.getType() : null;

      if (type instanceof JavaType.FullyQualified fq) {
        return fq;
      } else return null;
    }

    private static boolean isTypeListOfFactSpec(@Nullable JavaType.FullyQualified fq) {
      return fq != null
          && List.class.getName().equals(fq.getFullyQualifiedName())
          && hasTypeParamFactSpec(fq);
    }

    private static boolean methodDeclaredByProjection(@Nullable JavaType.Method methodType) {
      return methodType != null
          && methodType.getDeclaringType().isAssignableTo(Projection.class.getName());
    }

    private static boolean methodHasExactlyOneParameter(J.MethodDeclaration md) {
      return md.getParameters().size() == 1;
    }

    private static boolean methodIsCalledPostProcess(J.MethodDeclaration md) {
      return "postprocess".equals(md.getSimpleName());
    }

    private static boolean hasTypeParamFactSpec(JavaType.FullyQualified fq) {
      List<JavaType> typeParameters = fq.getTypeParameters();
      if (!typeParameters.isEmpty()) {
        {
          JavaType paramType = typeParameters.get(0);
          if (paramType instanceof JavaType.FullyQualified fqp) {
            return fqp.getFullyQualifiedName().equals(FactSpec.class.getName());
          }
        }
      }
      return false;
    }

    private TypeTree replaceListWithCollection(TypeTree typeExpr) {
      JavaType.ShallowClass collectionType =
          JavaType.ShallowClass.build(Collection.class.getName());

      if (typeExpr instanceof J.AnnotatedType annotated) {
        // Recursively replace the inner type expression, keeping the annotations intact
        return annotated.withTypeExpression(
            replaceListWithCollection(annotated.getTypeExpression()));
      }
      if (typeExpr instanceof J.ParameterizedType pt) {
        J.Identifier clazz = (J.Identifier) pt.getClazz();
        JavaType updatedType =
            pt.getType() instanceof JavaType.Parameterized ptt
                ? new JavaType.Parameterized(null, collectionType, ptt.getTypeParameters())
                : collectionType;
        return pt.withClazz(clazz.withSimpleName("Collection").withType(collectionType))
            .withType(updatedType);
      }
      if (typeExpr instanceof J.Identifier id) {
        return id.withSimpleName("Collection").withType(collectionType);
      }
      return typeExpr;
    }
  }
}
