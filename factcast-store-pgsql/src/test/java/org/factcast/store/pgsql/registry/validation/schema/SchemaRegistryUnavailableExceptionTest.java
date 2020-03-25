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
package org.factcast.store.pgsql.registry.validation.schema;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.factcast.store.pgsql.registry.SchemaRegistryUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import okhttp3.HttpUrl;

@ExtendWith(MockitoExtension.class)
public class SchemaRegistryUnavailableExceptionTest {

    @Test
    public void testSchemaRegistryUnavailableExceptionHttpUrlIntString() throws Exception {
        HttpUrl url = new HttpUrl.Builder().scheme("https")
                .host("www.google.com")
                .addPathSegment("search")
                .addQueryParameter("q", "polar bears")
                .build();
        SchemaRegistryUnavailableException uut = new SchemaRegistryUnavailableException(url
                .toString(), 403, "damnit");

        assertThat(uut.getMessage()).contains("403").contains("damnit").contains("bears");
    }

    @Test
    public void testWrapsException() throws Exception {
        IOException probe = new IOException("probe");
        SchemaRegistryUnavailableException uut = new SchemaRegistryUnavailableException(probe);

        assertSame(probe, uut.getCause());
    }

    @Test
    public void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new SchemaRegistryUnavailableException("url", 7, null);
        });
        assertThrows(NullPointerException.class, () -> {
            new SchemaRegistryUnavailableException(null, 7, "");
        });
        assertThrows(NullPointerException.class, () -> {
            new SchemaRegistryUnavailableException(null, 7, null);
        });
    }
}
