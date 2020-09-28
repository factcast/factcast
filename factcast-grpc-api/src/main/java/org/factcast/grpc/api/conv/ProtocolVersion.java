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
package org.factcast.grpc.api.conv;

import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

@With
@RequiredArgsConstructor(staticName = "of")
@Value
public class ProtocolVersion {

  int major;

  int minor;

  int patch;

  public boolean isCompatibleTo(ProtocolVersion other) {
    // patch level must be irrelevant
    return (major == other.major) && (minor <= other.minor);
  }

  @Override
  public String toString() {
    StringJoiner joiner = new StringJoiner(".");
    joiner.add(String.valueOf(major)).add(String.valueOf(minor)).add(String.valueOf(patch));
    return joiner.toString();
  }
}
