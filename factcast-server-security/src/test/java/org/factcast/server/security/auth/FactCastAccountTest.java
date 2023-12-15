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
package org.factcast.server.security.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

class FactCastAccountTest {

  private FactCastAccount uut = new FactCastAccount("foo");

  @Test
  void testDefaultsToFalse() {
    uut.initialize(Mockito.mock(FactCastAccessConfiguration.class));
    assertFalse(uut.canRead("foo"));
    assertFalse(uut.canWrite("foo"));
  }

  @Test
  void testReadOnly() {

    FactCastRole readOnlyRole = new FactCastRole();
    readOnlyRole.read().include().add("*");
    uut.role(readOnlyRole);

    assertTrue(uut.canRead("foo"));
    assertFalse(uut.canWrite("foo"));
  }

  @Test
  void testReadOnlyMultiRole() {

    FactCastRole other = new FactCastRole();
    other.read().exclude().add("toBeExcluded");

    FactCastRole readOnlyRole = new FactCastRole();
    readOnlyRole.read().include().add("*");
    uut.role(other);
    uut.role(readOnlyRole);

    assertTrue(uut.canRead("foo"));
    assertFalse(uut.canWrite("foo"));
  }

  @Test
  void testReadOnlyMultiRoleWithConflict() {

    FactCastRole role1 = new FactCastRole();
    role1.read().include().add("foo");

    FactCastRole role2 = new FactCastRole();
    role2.read().exclude().add("foo");

    uut.role(role1, role2);

    // exclusion wins
    assertFalse(uut.canRead("foo"));
    assertFalse(uut.canWrite("foo"));
  }

  @Test
  void testInilializationRuns() {

    FactCastRole role1 = new FactCastRole("r1");
    role1.read().include().add("foo");

    FactCastRole role2 = new FactCastRole("r2");
    role2.read().exclude().add("foo");

    uut.roleNames().add(role1.id());
    uut.roleNames().add(role2.id());

    FactCastAccessConfiguration cfg = new FactCastAccessConfiguration();
    cfg.roles().add(role1);
    cfg.roles().add(role2);
    cfg.accounts().add(uut);

    cfg.initialize();

    // exclusion wins
    assertTrue(uut.roles().contains(role1));
    assertTrue(uut.roles().contains(role2));
  }

  @Test
  void testfailsOnIdNull() {
    uut = new FactCastAccount();

    FactCastAccessConfiguration cfg = new FactCastAccessConfiguration();
    cfg.initialize();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> {
              uut.initialize(cfg);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void godPower() {
    assertTrue(FactCastAccount.GOD.canRead("anything"));
    assertTrue(FactCastAccount.GOD.canWrite("anything"));
  }

  @Test
  void cannotReadUninitialized() {
    var underTest = new FactCastAccount();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> {
              underTest.canRead("foo");
            })
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cannotWriteUninitialized() {
    var underTest = new FactCastAccount();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> {
              underTest.canWrite("foo");
            })
        .isInstanceOf(IllegalStateException.class);
  }
}
