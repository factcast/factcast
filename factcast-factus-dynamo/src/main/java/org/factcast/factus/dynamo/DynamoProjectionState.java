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
package org.factcast.factus.dynamo;

import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbImmutable(builder = DynamoProjectionState.DynamoProjectionStateBuilder.class)
public final class DynamoProjectionState {
  private final String key;

  private final UUID factStreamPosition;
  private final long serial;

  DynamoProjectionState(String key, UUID factStreamPosition, long serial) {
    this.key = key;
    this.factStreamPosition = factStreamPosition;
    this.serial = serial;
  }

  public static DynamoProjectionStateBuilder builder() {
    return new DynamoProjectionStateBuilder();
  }

  @DynamoDbPartitionKey
  public String key() {
    return this.key;
  }

  public UUID factStreamPosition() {
    return this.factStreamPosition;
  }

  public long serial() {
    return this.serial;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof DynamoProjectionState)) return false;
    final DynamoProjectionState other = (DynamoProjectionState) o;
    final Object this$key = this.key();
    final Object other$key = other.key();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) return false;
    final Object this$factStreamPosition = this.factStreamPosition();
    final Object other$factStreamPosition = other.factStreamPosition();
    if (this$factStreamPosition == null
        ? other$factStreamPosition != null
        : !this$factStreamPosition.equals(other$factStreamPosition)) return false;
    if (this.serial() != other.serial()) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = this.key();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $factStreamPosition = this.factStreamPosition();
    result = result * PRIME + ($factStreamPosition == null ? 43 : $factStreamPosition.hashCode());
    final long $serial = this.serial();
    result = result * PRIME + (int) ($serial >>> 32 ^ $serial);
    return result;
  }

  public String toString() {
    return "DynamoProjectionState(key="
        + this.key()
        + ", factStreamPosition="
        + this.factStreamPosition()
        + ", serial="
        + this.serial()
        + ")";
  }

  public static class DynamoProjectionStateBuilder {
    private String key;
    private UUID factStreamPosition;
    private long serial;

    DynamoProjectionStateBuilder() {}

    public DynamoProjectionStateBuilder key(String key) {
      this.key = key;
      return this;
    }

    public DynamoProjectionStateBuilder factStreamPosition(UUID factStreamPosition) {
      this.factStreamPosition = factStreamPosition;
      return this;
    }

    public DynamoProjectionStateBuilder serial(long serial) {
      this.serial = serial;
      return this;
    }

    public DynamoProjectionState build() {
      return new DynamoProjectionState(this.key, this.factStreamPosition, this.serial);
    }

    public String toString() {
      return "DynamoProjectionState.DynamoProjectionStateBuilder(key="
          + this.key
          + ", factStreamPosition="
          + this.factStreamPosition
          + ", serial="
          + this.serial
          + ")";
    }
  }
}
