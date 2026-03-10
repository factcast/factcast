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
        .parser(JavaParser.fromJavaVersion().classpath("lombok"));
  }

  @Test
  void changesListParamAndReturnType() {
    rewriteRun(
        java(
            "import java.util.List;\n"
                + "\n"
                + "class MyProjection {\n"
                + "    public List<String> postprocess(List<String> specsAsDiscovered) {\n"
                + "        return specsAsDiscovered;\n"
                + "    }\n"
                + "}\n",
            "import java.util.Collection;\n"
                + "import java.util.List;\n"
                + "\n"
                + "class MyProjection {\n"
                + "    public Collection<String> postprocess(Collection<String> specsAsDiscovered) {\n"
                + "        return specsAsDiscovered;\n"
                + "    }\n"
                + "}\n"));
  }

  @Test
  void changesAnnotatedReturnType() {
    rewriteRun(
        java(
            "import java.util.List;\n"
                + "import lombok.NonNull;\n"
                + "\n"
                + "class MyProjection {\n"
                + "    public @NonNull List<String> postprocess(@NonNull List<String> specsAsDiscovered) {\n"
                + "        return specsAsDiscovered;\n"
                + "    }\n"
                + "}\n",
            "import java.util.Collection;\n"
                + "import java.util.List;\n"
                + "import lombok.NonNull;\n"
                + "\n"
                + "class MyProjection {\n"
                + "    public @NonNull Collection<String> postprocess(@NonNull Collection<String> specsAsDiscovered) {\n"
                + "        return specsAsDiscovered;\n"
                + "    }\n"
                + "}\n"));
  }

  @Test
  void doesNotChangeOtherMethodNames() {
    rewriteRun(
        java(
            "import java.util.List;\n"
                + "\n"
                + "class MyProjection {\n"
                + "    public List<String> configure(List<String> specsAsDiscovered) {\n"
                + "        return specsAsDiscovered;\n"
                + "    }\n"
                + "}\n"));
  }

  @Test
  void doesNotChangeAlreadyMigratedMethod() {
    rewriteRun(
        java(
            "import java.util.Collection;\n"
                + "\n"
                + "class MyProjection {\n"
                + "    public Collection<String> postprocess(Collection<String> specsAsDiscovered) {\n"
                + "        return specsAsDiscovered;\n"
                + "    }\n"
                + "}\n"));
  }
}
