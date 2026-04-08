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

import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
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
  public String getDisplayName() {
    return "Migrate Projection.postprocess from List to Collection";
  }

  @Override
  public String getDescription() {
    return "Changes postprocess(List<FactSpec>) overrides to postprocess(Collection<FactSpec>).";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new PostprocessVisitor();
  }

  private static class PostprocessVisitor extends JavaIsoVisitor<ExecutionContext> {
    @Override
    public J.MethodDeclaration visitMethodDeclaration(
        J.MethodDeclaration md, ExecutionContext ctx) {
      md = super.visitMethodDeclaration(md, ctx);

      if (!isPostprocessWithListParam(md)) {
        return md;
      }

      J.VariableDeclarations param = (J.VariableDeclarations) md.getParameters().get(0);
      param = param.withTypeExpression(replaceListWithCollection(param.getTypeExpression()));
      md = md.withParameters(Collections.singletonList(param));
      md = md.withReturnTypeExpression(replaceListWithCollection(md.getReturnTypeExpression()));

      maybeAddImport("java.util.Collection");
      maybeRemoveImport("java.util.List");

      return md;
    }

    private boolean isPostprocessWithListParam(J.MethodDeclaration md) {
      if (!"postprocess".equals(md.getSimpleName())) {
        return false;
      }
      if (md.getParameters().size() != 1) {
        return false;
      }

      JavaType.Method methodType = md.getMethodType();
      if (methodType == null) {
        return false;
      }

      JavaType.FullyQualified declaringType = methodType.getDeclaringType();
      if (!declaringType.isAssignableTo("org.factcast.factus.projection.Projection")) {
        return false;
      }

      // Use typeExpression.getType() directly: J.VariableDeclarations.getType() delegates to
      // the inner NamedVariable's type which is not updated when we replace typeExpression.
      TypeTree typeExpr = ((J.VariableDeclarations) md.getParameters().get(0)).getTypeExpression();
      JavaType type = typeExpr != null ? typeExpr.getType() : null;

      if (!(type instanceof JavaType.FullyQualified fq)) {
        return false;
      }
      if (!"java.util.List".equals(fq.getFullyQualifiedName())) {
        return false;
      }
      return typeParamFactSpec(fq);
    }

    private boolean typeParamFactSpec(JavaType.FullyQualified fq) {
      List<JavaType> typeParameters = fq.getTypeParameters();
      if (typeParameters.isEmpty()) {
        return false;
      }
      JavaType paramType = typeParameters.get(0);
      if (paramType instanceof JavaType.FullyQualified fqp) {
        return fqp.getFullyQualifiedName().equals("org.factcast.core.spec.FactSpec");
      }
      return false;
    }

    private TypeTree replaceListWithCollection(TypeTree typeExpr) {
      JavaType.ShallowClass collectionType = JavaType.ShallowClass.build("java.util.Collection");

      if (typeExpr instanceof J.AnnotatedType) {
        J.AnnotatedType annotated = (J.AnnotatedType) typeExpr;
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
