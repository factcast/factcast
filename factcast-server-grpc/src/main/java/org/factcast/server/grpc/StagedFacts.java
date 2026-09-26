/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.server.grpc;

import com.google.protobuf.CodedOutputStream;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

public class StagedFacts {

  private static final int NOTIFICATION_TYPE_BYTES =
      CodedOutputStream.computeEnumSize(
          MSG_Notification.TYPE_FIELD_NUMBER, MSG_Notification.Type.Facts.getNumber());
  private static final int FACTS_TAG_BYTES =
      CodedOutputStream.computeTagSize(MSG_Notification.FACTS_FIELD_NUMBER);

  private final int maxInboundBytes;
  private final int targetBatchBytes;
  @Getter private int currentBytes;
  private int factsBytes;
  private final List<MSG_Fact> staged = new ArrayList<>(128);

  /** The normal batch target is 90% of the client-advertised inbound limit. */
  StagedFacts(int maxInboundBytes) {
    this.maxInboundBytes = maxInboundBytes;
    targetBatchBytes = maxInboundBytes - maxInboundBytes / 10;
  }

  public boolean add(@NonNull MSG_Fact fact) {
    int factBytes = CodedOutputStream.computeMessageSize(MSG_Facts.FACT_FIELD_NUMBER, fact);
    long singleNotificationBytes = notificationBytes(factBytes);
    if (singleNotificationBytes > maxInboundBytes) {
      throw Status.RESOURCE_EXHAUSTED
          .withDescription(
              "Fact notification requires "
                  + singleNotificationBytes
                  + " bytes, exceeding client inbound limit of "
                  + maxInboundBytes
                  + " bytes")
          .asRuntimeException();
    }

    long nextNotificationBytes = notificationBytes((long) factsBytes + factBytes);
    if (!staged.isEmpty() && nextNotificationBytes > targetBatchBytes) {
      return false;
    } else {
      staged.add(fact);
      factsBytes += factBytes;
      currentBytes = (int) nextNotificationBytes;
      return true;
    }
  }

  private static long notificationBytes(long factsBytes) {
    return NOTIFICATION_TYPE_BYTES
        + FACTS_TAG_BYTES
        + CodedOutputStream.computeUInt64SizeNoTag(factsBytes)
        + factsBytes;
  }

  public boolean isEmpty() {
    return staged.isEmpty();
  }

  /** size of the array (number of facts) */
  public int size() {
    return staged.size();
  }

  public MSG_Notification popAll() {
    MSG_Notification notification =
        MSG_Notification.newBuilder()
            .setType(MSG_Notification.Type.Facts)
            .setFacts(MSG_Facts.newBuilder().addAllFact(staged))
            .build();
    staged.clear();
    factsBytes = 0;
    currentBytes = 0;
    return notification;
  }
}
