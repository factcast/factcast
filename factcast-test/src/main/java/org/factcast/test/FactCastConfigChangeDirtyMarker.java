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
package org.factcast.test;

import java.util.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class FactCastConfigChangeDirtyMarker implements TestExecutionListener {
  @Override
  public void afterTestClass(TestContext testContext) throws Exception {
    TestExecutionListener.super.afterTestClass(testContext);
    FactcastTestConfig.Config config =
        Optional.ofNullable(testContext.getTestClass().getAnnotation(FactcastTestConfig.class))
            .map(FactcastTestConfig.Config::from)
            .orElse(FactcastTestConfig.Config.defaults());

    if (!config.equals(FactcastTestConfig.Config.defaults()))
      testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.CURRENT_LEVEL);
  }
}
