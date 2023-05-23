/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.factus;

import static org.assertj.core.api.Assertions.*;

import org.factcast.factus.SuppressFactusWarnings.Warning;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WarningTest {

  public static class NoSupression {
    @Handler
    public void apply(Object o) {}
  }

  public static class AllSupressionMethod {
    @Handler
    @SuppressFactusWarnings(Warning.ALL)
    public void apply(Object o) {}
  }

  @SuppressFactusWarnings(Warning.ALL)
  public static class AllSupressionType {
    @Handler
    public void apply(Object o) {}
  }

  public static class SpecificSupressionMethod {
    @Handler
    @SuppressFactusWarnings(Warning.PUBLIC_HANDLER_METHOD)
    public void apply(Object o) {}

    public Object field;
  }

  @SuppressFactusWarnings(Warning.PUBLIC_HANDLER_METHOD)
  public static class SpecificSupressionType {
    @Handler
    public void apply(Object o) {}

    public Object field;
  }

  @Test
  void findSuppression() throws NoSuchMethodException, NoSuchFieldException {

    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                NoSupression.class.getMethod("apply", Object.class)))
        .isFalse();

    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                AllSupressionMethod.class.getMethod("apply", Object.class)))
        .isTrue();
    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                AllSupressionType.class.getMethod("apply", Object.class)))
        .isTrue();
    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                SpecificSupressionMethod.class.getMethod("apply", Object.class)))
        .isTrue();
    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                SpecificSupressionType.class.getMethod("apply", Object.class)))
        .isTrue();
    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                SpecificSupressionMethod.class.getField("field")))
        .isFalse();
    assertThat(
            Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(
                SpecificSupressionType.class.getField("field")))
        .isTrue();
  }
}
