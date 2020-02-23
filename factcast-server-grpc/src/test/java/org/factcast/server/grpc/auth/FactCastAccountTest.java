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
package org.factcast.server.grpc.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class FactCastAccountTest {

    private FactCastAccount uut = new FactCastAccount("foo", "bar");

    @Test
    public void testDefaultsToFalse() throws Exception {
        assertFalse(uut.canRead("foo"));
        assertFalse(uut.canWrite("foo"));
    }

    @Test
    public void testReadOnly() throws Exception {

        FactCastRole readOnlyRole = new FactCastRole();
        readOnlyRole.read().include().add("*");
        uut.roles().add(readOnlyRole);

        assertTrue(uut.canRead("foo"));
        assertFalse(uut.canWrite("foo"));
    }

    @Test
    public void testReadOnlyMultiRole() throws Exception {

        FactCastRole other = new FactCastRole();
        other.read().exclude().add("toBeExcluded");

        FactCastRole readOnlyRole = new FactCastRole();
        readOnlyRole.read().include().add("*");
        uut.roles().add(other);
        uut.roles().add(readOnlyRole);

        assertTrue(uut.canRead("foo"));
        assertFalse(uut.canWrite("foo"));
    }

    @Test
    public void testReadOnlyMultiRoleWithConflict() throws Exception {

        FactCastRole role1 = new FactCastRole();
        role1.read().include().add("foo");

        FactCastRole role2 = new FactCastRole();
        role2.read().exclude().add("foo");

        uut.roles().add(role1);
        uut.roles().add(role2);

        // exclusion wins
        assertFalse(uut.canRead("foo"));
        assertFalse(uut.canWrite("foo"));
    }

}
