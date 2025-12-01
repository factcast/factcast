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

import static java.util.Optional.ofNullable;

import com.mongodb.client.MongoCollection;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import lombok.SneakyThrows;
import org.bson.Document;
import org.factcast.factus.Handler;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;

public interface MongoDbUserNames {

  MongoCollection<UserNamesSchema> userNames();

  default int count() {
    return (int) userNames().countDocuments();
  }

  default boolean contains(String name) {
    return ofNullable(userNames().find(new Document("userName", name)).first()).isPresent();
  }

  @SneakyThrows
  @Handler
  default void apply(UserCreated created) {
    final var userId = created.aggregateId();
    userNames().insertOne(new UserNamesSchema().userId(userId).userName(created.userName()));
  }

  @SneakyThrows
  @Handler
  default void apply(UserDeleted deleted) {
    userNames().deleteOne(new Document("userName", deleted.aggregateId()));
  }

  @Data
  class UserNamesSchema implements Serializable {
    private UUID userId;
    private String userName;
    private boolean someBoolField;

    public UUID getUserId() {
      return this.userId;
    }
  }
}
