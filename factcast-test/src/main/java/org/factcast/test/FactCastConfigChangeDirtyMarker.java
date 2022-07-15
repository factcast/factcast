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
