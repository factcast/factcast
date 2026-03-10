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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.mongodb.client.MongoClient;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.itests.factus.proj.UserV1;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@RequiredArgsConstructor
public abstract class SnapshotCachePerformanceTest extends AbstractFactCastIntegrationTest {

  public static final SnapshotSerializerId SNAPSHOT_SERIALIZER_ID = SnapshotSerializerId.of("narf");
  final SnapshotCache repository;
  private MongoClient mongoClient;

  @Autowired
  public void setMongoClient(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  @Test
  @Disabled
  public void roundTripTest() {
    // TODO do insert, and per insert do 2 updates
    //      mongoClient.getDatabase("factcast").getCollection("factus_snapshot").deleteMany(new
    // Document());
    int roundTrips = 20000;
    Stopwatch sw = Stopwatch.createStarted();
    for (int i = 0; i < roundTrips; i++) {
      UUID randomUUID = new UUID(i, i);
      SnapshotIdentifier id = SnapshotIdentifier.of(UserV1.class, randomUUID);
      repository.store(id, new SnapshotData(dummyEvent, SNAPSHOT_SERIALIZER_ID, randomUUID));
    }

    log.info(
        "Performed {} roundtrips in {} ms", roundTrips, sw.stop().elapsed(TimeUnit.MILLISECONDS));
  }

  @Test
  @Disabled
  public void readTest() {
    int roundTrips = 20000;
    Set<SnapshotIdentifier> ids = new HashSet<>();
    for (int i = 0; i < roundTrips; i++) {
      UUID randomUUID = new UUID(i, i);
      SnapshotIdentifier id = SnapshotIdentifier.of(UserV1.class, randomUUID);
      repository.store(id, new SnapshotData(dummyEvent, SNAPSHOT_SERIALIZER_ID, randomUUID));
      ids.add(id);
    }

    Stopwatch sw = Stopwatch.createStarted();
    for (var id : ids) {
      Optional<SnapshotData> found = repository.find(id);
      assertThat(found).isPresent();
    }
    log.info("Performed {} reads in {} ms", ids.size(), sw.stop().elapsed(TimeUnit.MILLISECONDS));
  }

  private static final byte[] dummyEvent =
      """
[ {
  "header" : {
    "id" : "99f1a9af-a4db-11f0-b56f-02cf7edc73b7",
    "ns" : "networkpoints",
    "meta" : {
      "_ts" : 1759992353902,
      "_ser" : 3358230,
      "source" : "pickup-service",
      "actorOrganisation" : "3e5c5429-5deb-11ee-a653-02ca52e7caeb"
    },
    "type" : "NetworkPointChanged",
    "aggIds" : [ "a80274a9-20e8-11f0-b517-060c638aff41" ],
    "version" : 5
  },
  "payload" : {
    "meta" : {
      "amr" : [ "UserAccount:3e5d177a-5deb-11ee-a653-02ca52e7caeb", "monolith" ],
      "timestamp" : "2025-10-09T08:45:53.546+02:00",
      "organisation" : "3e5c5429-5deb-11ee-a653-02ca52e7caeb"
    },
    "timeSlices" : [ {
      "eic" : "10X1001A1001A248",
      "name" : "NPN Exit",
      "active" : true,
      "gasType" : "HGAS",
      "direction" : "EXIT",
      "validFrom" : "2025-04-24T10:46:47.163+02:00",
      "categories" : [ "8ed4e2e4-9e81-11ea-bb37-0242ac130002" ],
      "congestion" : false,
      "flowUnitId" : "5bd05688-9f23-11ea-bb37-0242ac130002",
      "adjacentTso" : "",
      "marketAreaId" : "abb8ad21-5dd8-11ee-a653-02ca52e7caeb",
      "floatingCharge" : true,
      "sellingMethods" : [ "AUCTION_LONG_TERM" ],
      "tradingMarkets" : [ "PRIMARY", "SECONDARY" ],
      "specificSecondary" : false,
      "networkPointTypeId" : "52c09680-9f3b-11ea-bb37-0242ac130002",
      "specificConditions" : [ ],
      "undiscountedCharge" : false,
      "ownerOrganisationId" : "3e5c5429-5deb-11ee-a653-02ca52e7caeb",
      "publicAssignedNetworkPointId" : "NPN2",
      "internalAssignedNetworkPointId" : "NPN2"
    }, {
      "eic" : "10X1001A1001A248",
      "name" : "NPN ExitTest",
      "active" : true,
      "gasType" : "HGAS",
      "direction" : "EXIT",
      "validFrom" : "2025-10-09T08:45:53.489+02:00",
      "categories" : [ "8ed4e2e4-9e81-11ea-bb37-0242ac130002" ],
      "congestion" : false,
      "flowUnitId" : "5bd05688-9f23-11ea-bb37-0242ac130002",
      "adjacentTso" : "",
      "marketAreaId" : "abb8ad21-5dd8-11ee-a653-02ca52e7caeb",
      "floatingCharge" : true,
      "sellingMethods" : [ "AUCTION_LONG_TERM" ],
      "tradingMarkets" : [ "PRIMARY", "SECONDARY" ],
      "specificSecondary" : false,
      "networkPointTypeId" : "52c09680-9f3b-11ea-bb37-0242ac130002",
      "specificConditions" : [ ],
      "undiscountedCharge" : false,
      "ownerOrganisationId" : "3e5c5429-5deb-11ee-a653-02ca52e7caeb",
      "publicAssignedNetworkPointId" : "NPN2",
      "internalAssignedNetworkPointId" : "NPN2"
    } ],
    "networkPointId" : "a80274a9-20e8-11f0-b517-060c638aff41"
  }
} ]
"""
          .getBytes();
}
