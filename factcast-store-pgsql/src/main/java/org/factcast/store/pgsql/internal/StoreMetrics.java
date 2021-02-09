/*
 * Copyright © 2017-2021 factcast.org
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
package org.factcast.store.pgsql.internal;

import lombok.Getter;
import lombok.NonNull;

public class StoreMetrics {

  public enum OP {
    PUBLISH("publish"),

    SUBSCRIBE_FOLLOW("subscribe-follow"),

    SUBSCRIBE_CATCHUP("subscribe-catchup"),

    FETCH_BY_ID("fetchById"),

    SERIAL_OF("serialOf"),

    ENUMERATE_NAMESPACES("enumerateNamespaces"),

    ENUMERATE_TYPES("enumerateTypes"),

    GET_STATE_FOR("getStateFor"),

    PUBLISH_IF_UNCHANGED("publishIfUnchanged"),

    GET_SNAPSHOT("getSnapshot"),

    SET_SNAPSHOT("setSnapshot"),

    CLEAR_SNAPSHOT("clearSnapshot"),

    COMPACT_SNAPSHOT_CACHE("compactSnapshotCache"),

    NOTIFY_ROUNDTRIP_LATENCY("notifyDatabaseRoundTripLatency"),

    MISSED_ROUNDTRIP("missedDatabaseRoundtrip");

    @NonNull @Getter final String op;

    OP(@NonNull String op) {
      this.op = op;
    }
  }

  static final String DURATION_METRIC_NAME = "factcast.store.operations.duration";

  static final String COUNTER_METRIC_NAME = "factcast.store.operations.count";

  static final String TAG_STORE_KEY = "store";

  static final String TAG_STORE_VALUE = "pgsql";

  static final String TAG_OPERATION_KEY = "operation";

  static final String TAG_EXCEPTION_KEY = "exception";

  static final String TAG_EXCEPTION_VALUE_NONE = "None";
}
