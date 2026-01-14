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
package org.factcast.itests.factus.client;

import java.util.Base64;
import org.assertj.core.api.Assertions;
import org.factcast.ExampleSnapshotProjection;
import org.factcast.factus.Factus;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.itests.TestFactusApplication;
import org.factcast.spring.boot.autoconfigure.snap.RedissonSnapshotCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonSnapshotCacheAutoConfiguration.class})
public class RedissonSnapshotCacheTest extends SnapshotCacheTest {

  public RedissonSnapshotCacheTest(SnapshotCache repository) {
    super(repository);
  }

  @Autowired RedissonClient c;

  @Autowired Factus f;

  @Test
  void fallsbackToLegacySnapshot() {

    // create legacy snaptshot
    String key =
        "org.factcast.ExampleSnapshotProjection_1_ProjectionSnapshotRepositoryImpl_JacksonSnapshotSerializer00000000-0000-0000-0000-000000000000";
    byte[] value =
        Base64.getDecoder()
            .decode(
                "BAQJPh9vcmcuZmFjdGNhc3QuY29yZS5zbmFwLlNuYXBzaG904ySuNcwRfaUAAAAEPgVieXRlcxYAPgpjb21wcmVzc2VkIAA+AmlkFgA+CGxhc3RGYWN0FgAWQhUheyJzdGF0ZSI6InByb2Nlc3NlZCJ9AAQJPiFvcmcuZmFjdGNhc3QuY29yZS5zbmFwLlNuYXBzaG90SWTTfJIRvYC1LQAAAAI+A2tleRYAPgR1dWlkFgAWPmNvcmcuZmFjdGNhc3QuRXhhbXBsZVNuYXBzaG90UHJvamVjdGlvbl8xX1Byb2plY3Rpb25TbmFwc2hvdFJlcG9zaXRvcnlJbXBsX0phY2tzb25TbmFwc2hvdFNlcmlhbGl6ZXIECT4OamF2YS51dGlsLlVVSUS8mQP3mG2FLwAAAAI+DGxlYXN0U2lnQml0cyQAPgttb3N0U2lnQml0cyQAFgAAAAAAAAAAAAAAAAAAAAAEO/+XMaEkTa5DYNfjTwaKeUvP");
    c.getBucket(key, ByteArrayCodec.INSTANCE).set(value);

    //
    ExampleSnapshotProjection restoredLegacySnapshot = f.fetch(ExampleSnapshotProjection.class);
    // should contain processed as serialized
    Assertions.assertThat(restoredLegacySnapshot)
        .isNotNull()
        .extracting("state")
        .isEqualTo("processed");
  }

  @Test
  void convertsLegacySnapshot() {

    // no snapshot in redis by now
    Assertions.assertThat(c.getKeys().count()).isZero();
    Assertions.assertThat(f.fetch(ExampleSnapshotProjection.class))
        .isNotNull()
        .extracting("state")
        .isEqualTo("new");
    // should not migrate an empty snap
    Assertions.assertThat(c.getKeys().count()).isZero();

    // create legacy snapshot
    String key =
        "org.factcast.ExampleSnapshotProjection_1_ProjectionSnapshotRepositoryImpl_JacksonSnapshotSerializer00000000-0000-0000-0000-000000000000";
    byte[] value =
        Base64.getDecoder()
            .decode(
                "BAQJPh9vcmcuZmFjdGNhc3QuY29yZS5zbmFwLlNuYXBzaG904ySuNcwRfaUAAAAEPgVieXRlcxYAPgpjb21wcmVzc2VkIAA+AmlkFgA+CGxhc3RGYWN0FgAWQhUheyJzdGF0ZSI6InByb2Nlc3NlZCJ9AAQJPiFvcmcuZmFjdGNhc3QuY29yZS5zbmFwLlNuYXBzaG90SWTTfJIRvYC1LQAAAAI+A2tleRYAPgR1dWlkFgAWPmNvcmcuZmFjdGNhc3QuRXhhbXBsZVNuYXBzaG90UHJvamVjdGlvbl8xX1Byb2plY3Rpb25TbmFwc2hvdFJlcG9zaXRvcnlJbXBsX0phY2tzb25TbmFwc2hvdFNlcmlhbGl6ZXIECT4OamF2YS51dGlsLlVVSUS8mQP3mG2FLwAAAAI+DGxlYXN0U2lnQml0cyQAPgttb3N0U2lnQml0cyQAFgAAAAAAAAAAAAAAAAAAAAAEO/+XMaEkTa5DYNfjTwaKeUvP");
    c.getBucket(key, ByteArrayCodec.INSTANCE).set(value);
    Assertions.assertThat(c.getKeys().count()).isOne();

    ExampleSnapshotProjection restoredLegacySnapshot = f.fetch(ExampleSnapshotProjection.class);
    // should contain processed as serialized
    Assertions.assertThat(restoredLegacySnapshot)
        .isNotNull()
        .extracting("state")
        .isEqualTo("processed");

    // expecting the old snapshot to be migrated already
    Assertions.assertThat(c.getKeys().count()).isEqualTo(2L);

    // remove legacy snap
    c.getBucket(key, ByteArrayCodec.INSTANCE).delete();
    Assertions.assertThat(c.getKeys().count()).isOne();

    // still we deser from migrated snapshot now
    Assertions.assertThat(restoredLegacySnapshot)
        .isNotNull()
        .extracting("state")
        .isEqualTo("processed");
  }
}
