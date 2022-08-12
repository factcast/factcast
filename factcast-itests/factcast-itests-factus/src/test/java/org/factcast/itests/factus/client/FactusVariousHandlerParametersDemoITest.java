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
package org.factcast.itests.factus.client;

import java.util.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class FactusVariousHandlerParametersDemoITest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  static class ProjectionWithVariousHandlerParameters extends LocalManagedProjection {

    @Handler
    void apply(SomethingStarted event) {
      System.out.println("handling SomethingStarted: ");
      System.out.println("event content: " + event);
    }

    @Handler
    void apply(SomethingChanged event, FactHeader header) {
      System.out.println("handling SomethingChanged");
      System.out.println("Namespace: " + header.ns());
      System.out.println("Event type: " + header.type());
      System.out.println("Event version: " + header.version());
      System.out.println("FilterByMeta entries: " + header.meta());
    }

    @Handler
    void apply(SomethingElseChanged event, UUID factId) {
      System.out.println("handling SomethingElseChanged");
      System.out.println("Fact ID: " + factId);
    }

    @Handler
    void apply(SomethingDeactivated event, Fact fact) {
      System.out.println("handling SomethingDeactivated");
      System.out.println("Fact header as JSON: " + fact.jsonHeader());
      System.out.println("Payload as JSON: " + fact.jsonPayload());
      // also access to namespace, version, meta data...
    }

    @Handler
    void apply(SomethingReactivated event, FactHeader factHeader, UUID factId, Fact fact) {
      System.out.println("handling SomethingReactivated");
    }

    @HandlerFor(ns = "test", type = "SomethingAdded")
    void applySomethingAdded(Fact fact) {
      System.out.println("handling SomethingAdded");
      System.out.println("handling version: " + fact.version());
    }

    @HandlerFor(ns = "test", type = "SomethingRemoved", version = 2)
    void applySomethingRemoved(FactHeader factHeader, UUID factId, Fact fact) {
      System.out.println("handling SomethingRemoved");
    }
  }

  @Test
  public void updateLocalProjection() {
    // publish events
    SomethingStarted somethingStarted = new SomethingStarted();
    somethingStarted.someProperty("some value");
    factus.publish(somethingStarted);

    SomethingChanged somethingChanged = new SomethingChanged();
    somethingChanged.someProperty("some value");
    factus.publish(somethingChanged);

    SomethingElseChanged somethingElseChanged = new SomethingElseChanged();
    somethingElseChanged.someProperty("some value");
    factus.publish(somethingElseChanged);

    SomethingDeactivated somethingDeactivated = new SomethingDeactivated();
    somethingDeactivated.someProperty("some value");
    factus.publish(somethingDeactivated);

    SomethingReactivated somethingReactivated = new SomethingReactivated();
    somethingReactivated.someProperty("some value");
    factus.publish(somethingReactivated);

    SomethingAdded somethingAdded = new SomethingAdded();
    somethingAdded.someProperty("some value");
    factus.publish(somethingAdded);

    SomethingRemoved somethingRemoved = new SomethingRemoved();
    somethingRemoved.someProperty("some value");
    factus.publish(somethingRemoved);

    ProjectionWithVariousHandlerParameters projection =
        new ProjectionWithVariousHandlerParameters();
    factus.update(projection);
  }

  @Specification(ns = "test")
  static class SomethingStarted extends MinimalBaseEvent {}

  @Specification(ns = "test")
  static class SomethingChanged extends MinimalBaseEvent {}

  @Specification(ns = "test")
  static class SomethingElseChanged extends MinimalBaseEvent {}

  @Specification(ns = "test")
  static class SomethingDeactivated extends MinimalBaseEvent {}

  @Specification(ns = "test")
  static class SomethingReactivated extends MinimalBaseEvent {}

  @Specification(ns = "test", version = 1)
  static class SomethingAdded extends MinimalBaseEvent {}

  @Specification(ns = "test", version = 2)
  static class SomethingRemoved extends MinimalBaseEvent {}

  @Data
  abstract static class MinimalBaseEvent implements EventObject {
    private String someProperty;

    @Override
    public Set<UUID> aggregateIds() {
      return Collections.emptySet();
    }
  }
}
