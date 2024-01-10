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
package org.factcast.core;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.util.FactCastJson;

/**
 * Note: creating an instance involves deserializing the header from JS. This is probably not
 * optimal considering performance. If you extend FactCast, consider creating a dedicated Fact Impl.
 *
 * <p>For caching purposes, this thing should be Externalizable.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 * @see Fact
 */
@EqualsAndHashCode(of = {"deserializedHeader"})
public class DefaultFact implements Fact, Externalizable {

  @Override
  public String toString() {
    return "DefaultFact [id=" + deserializedHeader.id() + "]";
  }

  @Getter String jsonHeader;

  @Getter String jsonPayload;

  transient FactHeader deserializedHeader;

  // needed for Externalizable – do not use !
  @Deprecated
  public DefaultFact() {}

  public static Fact of(@NonNull String jsonHeader, @NonNull String jsonPayload) {
    return new DefaultFact(jsonHeader, jsonPayload);
  }

  @Override
  public @NonNull FactHeader header() {
    return deserializedHeader;
  }

  @SneakyThrows
  protected DefaultFact(String jsonHeader, String jsonPayload) {
    this.jsonHeader = jsonHeader;
    this.jsonPayload = jsonPayload;
    init(jsonHeader);
  }

  public DefaultFact(@NonNull FactHeader header, @NonNull String payload) {
    deserializedHeader = header;
    jsonPayload = payload;
    jsonHeader = FactCastJson.writeValueAsString(header);
    validate();
  }

  private void init(String jsonHeader) {
    deserializedHeader = FactCastJson.readValue(FactHeader.class, jsonHeader);
    validate();
  }

  private void validate() {
    if (deserializedHeader.id() == null) {
      throw new IllegalArgumentException("id attribute missing from " + jsonHeader);
    }
    if (deserializedHeader.ns() == null || deserializedHeader.ns().trim().isEmpty()) {
      throw new IllegalArgumentException("ns attribute missing from " + jsonHeader);
    }
    if (deserializedHeader.version() < 0)
      throw new IllegalArgumentException("version attribute is not valid " + jsonHeader);
  }

  @Override
  @Deprecated
  public String meta(String key) {
    return deserializedHeader.meta(key);
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    // write only header & payload
    out.writeUTF(jsonHeader);
    out.writeUTF(jsonPayload);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException {
    // read only header & payload
    jsonHeader = in.readUTF();
    jsonPayload = in.readUTF();
    // and recreate the header fields
    init(jsonHeader);
  }

  @Override
  public @NonNull UUID id() {
    return deserializedHeader.id();
  }

  @Override
  public int version() {
    return deserializedHeader.version();
  }

  @Override
  public @NonNull String ns() {
    return deserializedHeader.ns();
  }

  @Override
  public String type() {
    return deserializedHeader.type();
  }

  @Override
  public Set<UUID> aggIds() {
    return deserializedHeader.aggIds();
  }
}
