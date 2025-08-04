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
package org.factcast.itests.factus.proj;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.mongodb.AbstractMongoDbManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;

@Slf4j
@ProjectionMetaData(revision = 1)
public class MongoDbManagedUserNames extends AbstractMongoDbManagedProjection
    implements MongoDbUserNames {

  private final MongoCollection<UserNamesSchema> userNames;

  public MongoDbManagedUserNames(MongoDatabase mongoDb) {
    super(mongoDb);
    this.userNames = mongoDb.getCollection("UserNames", UserNamesSchema.class);
  }

  public MongoCollection<UserNamesSchema> userNames() {
    return userNames;
  }

  public long getLastProcessedSerialFromState() {
    return this.mongoDb().getCollection("states").find().first().get("lastFactSerial", Long.class);
  }
}
