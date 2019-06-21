/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.server.grpc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class CredentialConfigurationImplTest {

    @Test
    public void testReadHappyPath() throws Exception {
        CredentialConfiguration cc = CredentialConfiguration.read(render("", ""));
        assertThat(cc).isNotNull();
        assertThat(cc.fullAccess()).isEmpty();
        assertThat(cc.readOnlyAccess()).isEmpty();

        cc = CredentialConfiguration.read(render("{\"name\":\"foo\",\"password\":\"bar\"}",
                "{\"name\":\"guest\",\"password\":\"guest\"},{\"name\":\"reader\",\"password\":\"pwd\"}"));
        {
            assertThat(cc).isNotNull();
            assertThat(cc.fullAccess()).isNotEmpty();
            assertThat(cc.fullAccess()).hasSize(1);
            FullAccessCredential first = cc.fullAccess().get(0);
            assertThat(first.name()).isEqualTo("foo");
            assertThat(first.password()).isEqualTo("bar");
        }
        {
            assertThat(cc.readOnlyAccess()).hasSize(2);
            ReadOnlyAccessCredential first = cc.readOnlyAccess().get(0);
            ReadOnlyAccessCredential second = cc.readOnlyAccess().get(1);
            assertThat(first.name()).isEqualTo("guest");
            assertThat(first.password()).isEqualTo("guest");
            assertThat(second.name()).isEqualTo("reader");
            assertThat(second.password()).isEqualTo("pwd");
        }
        
        cc = CredentialConfiguration.read("{}");
        {
            assertThat(cc).isNotNull();
            assertThat(cc.fullAccess()).isEmpty();
            assertThat(cc.readOnlyAccess()).isEmpty();
        }
    }

    private String render(String full, String read) {
        return "{\"fullAccess\":[" + full + "], \"readOnlyAccess\":[" + read + "]}";
    }

}
