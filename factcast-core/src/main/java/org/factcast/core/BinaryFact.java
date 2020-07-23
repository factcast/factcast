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

import org.factcast.core.util.FactCastJson;
import org.factcast.core.util.FactCastJson.Encoder;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.NonNull;
import lombok.SneakyThrows;

public class BinaryFact extends DefaultFact implements Externalizable {
    private static final int serialVersionUid = 1;

    private Encoder encoder;

    private byte[] bytePayload;

    private byte[] byteHeader;

    public BinaryFact() {
    }

    @SneakyThrows
    private BinaryFact(Encoder encoder, byte[] header, byte[] payload) {
        this.encoder = encoder;
        this.byteHeader = header;
        this.bytePayload = payload;
        init();
    }

    private void init() throws IOException {
        this.deserializedHeader = encoder.get().readerFor(Header.class).readValue(header());
        validate();
    }

    public static Fact of(@NonNull FactCastJson.Encoder encoder, @NonNull byte[] header,
            @NonNull byte[] payload) {
        return new BinaryFact(encoder, header, payload);
    }

    @SneakyThrows
    @Override
    @Deprecated
    /**
     * expensive to call on BinaryFact. please try to avoid it.
     *
     * see <code>Fact.payloadAs(Class clazz)</code>
     */
    public String jsonPayload() {
        if (jsonPayload == null) {
            jsonPayload = payload().toString();
        }
        return jsonPayload;
    }

    @Override
    @Deprecated
    public String jsonHeader() {
        if (jsonHeader == null) {
            jsonHeader = header().toString();
        }
        return jsonHeader;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        // write only header & payload
        out.write(encoder.ordinal());
        out.writeObject(byteHeader);
        out.writeObject(bytePayload);
        out.flush();
    }

    @SneakyThrows
    @Override
    public void readExternal(ObjectInput in) throws IOException {
        // read only header & payload
        int ord = in.read();
        encoder = FactCastJson.Encoder.values()[ord];
        byteHeader = (byte[]) in.readObject();
        bytePayload = (byte[]) in.readObject();
        init();
    }

    @SneakyThrows
    @Override
    public JsonNode payload() {
        if (parsedPayload == null)
            parsedPayload = FactCastJson.readTree(bytePayload, encoder);
        return parsedPayload;
    }

    @SneakyThrows
    @Override
    public JsonNode header() {
        if (parsedHeader == null)
            parsedHeader = FactCastJson.readTree(byteHeader, encoder);
        return parsedHeader;
    }
}
