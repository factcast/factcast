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
package org.factcast.integration.transformation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.FactValidationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.registry.transformation.chains.MissingTransformationInformation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import lombok.val;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TransformationTest {

    @Autowired
    FactCast fc;

    @Test
    public void publishAndFetchBack() throws Exception, TimeoutException {
        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
        fc.publish(f);

        val found = fc.fetchById(id).orElse(null);
        assertNotNull(found);
        assertEquals(f.ns(), found.ns());
        assertEquals(f.type(), found.type());
        assertEquals(f.id(), found.id());
    }

    // private Fact findFirst(UUID id, Integer version) throws
    // TransformationException {
    // if (version == null)
    // return fc.fetchById(id).orElse(null);
    // else
    // return fc.fetchByIdAndVersion(id, version).orElse(null);
    //
    // // @NonNull
    // // AtomicReference<Fact> ret = new AtomicReference<Fact>(null);
    // //
    // // FactSpec spec = FactSpec.ns("users")
    // // .type("UserCreated")
    // // .aggId(id);
    // //
    // // if (version != null)
    // // spec.version(version);
    // //
    // // try (Subscription sub =
    // // fc.subscribe(SubscriptionRequest.catchup(spec)
    // // .fromScratch(),
    // // f -> {
    // // if (ret.get() == null)
    // // ret.set(f);
    // // }).awaitCatchup(10000);) {
    // // }
    // // return ret.get();
    // }

    @Test
    public void publishV1AndFetchBackAsV2() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
        fc.publish(f);

        val found = fc.fetchByIdAndVersion(id, 2).orElse(null);

        assertNotNull(found);
        assertEquals(f.ns(), found.ns());
        assertEquals(f.type(), found.type());
        assertEquals(f.id(), found.id());
        assertEquals(2, found.version());
    }

    private Fact createTestFact(UUID id, int version, String body) {
        return Fact.builder()
                .ns("users")
                .type("UserCreated")
                .id(id)
                .version(version)
                .build(body);
    }

    @Test
    public void publishV2AndFetchBackAsV1() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 2,
                "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\"}");
        fc.publish(f);

        val found = fc.fetchByIdAndVersion(id, 1).orElse(null);
        assertNotNull(found);
        assertEquals(f.ns(), found.ns());
        assertEquals(f.type(), found.type());
        assertEquals(f.id(), found.id());
        assertEquals(1, found.version());
    }

    @Test
    public void downcastUsesNonSyntheticTransformation() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 3,
                "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"PETER PETERSON\"}");
        fc.publish(f);

        val found = fc.fetchByIdAndVersion(id, 1).orElse(null);
        assertNotNull(found);
        assertEquals(f.ns(), found.ns());
        assertEquals(f.type(), found.type());
        assertEquals(f.id(), found.id());
        assertEquals(1, found.version());
        assertTrue(getBoolean(found, "wasDowncastedFromVersion3to2"));
    }

    @Test
    public void returnsOriginalIfNoVersionSet() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 3,
                "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"PETER PETERSON\"}");
        fc.publish(f);
        val found = fc.fetchById(id).orElse(null);
        assertNotNull(found);
        assertEquals(3, found.version());
    }

    @Test
    public void returnsOriginalIfVersionSetTo0() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 3,
                "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"PETER PETERSON\"}");
        fc.publish(f);

        val found = fc.fetchByIdAndVersion(id, 0).orElse(null);
        assertNotNull(found);
        assertEquals(3, found.version());
    }

    @Test
    public void publishV1AndFetchBackAsV3() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
        fc.publish(f);

        val found = fc.fetchByIdAndVersion(id, 3).orElse(null);
        assertNotNull(f);
        assertEquals(f.ns(), found.ns());
        assertEquals(f.type(), found.type());
        assertEquals(f.id(), found.id());
        assertEquals(3, found.version());
        assertEquals("Peter Peterson", getString(found, "displayName"));
    }

    @Test
    public void publishV1AndFetchBackAsUnknownVersionMustFail() throws Exception, TimeoutException {

        UUID id = UUID.randomUUID();
        Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
        fc.publish(f);

        try {
            val found = fc.fetchByIdAndVersion(id, 999).orElse(null);
            fail("should have thrown");
        } catch (MissingTransformationInformation expected) {
        } catch (Exception anyOther) {
            fail("unexpected Exception", anyOther);
        }
    }

    @Test
    public void validationFailsOnSchemaViolation() {
        Fact brokenFact = createTestFact(UUID.randomUUID(), 1, "{}");
        assertThrows(FactValidationException.class, () -> {
            fc.publish(brokenFact);
        });
    }

    private String getString(Fact f, String name) throws JsonMappingException,
            JsonProcessingException {
        return FactCastJson.readTree(f.jsonPayload())
                .path(name)
                .asText();
    }

    private boolean getBoolean(Fact f, String name) throws JsonMappingException,
            JsonProcessingException {
        return FactCastJson.readTree(f.jsonPayload())
                .path(name)
                .asBoolean();
    }
}
