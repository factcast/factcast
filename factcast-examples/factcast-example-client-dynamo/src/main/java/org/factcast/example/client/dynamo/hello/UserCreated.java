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
package org.factcast.example.client.dynamo.hello;

import lombok.*;
import org.factcast.factus.event.Specification;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Specification(ns = "users", version = 3)
@ToString
@Value
@Builder
@DynamoDbImmutable(builder = UserCreated.UserCreatedBuilder.class)
public class UserCreated {
  String lastName;

  String firstName;

  String salutation;

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  String displayName;
}
