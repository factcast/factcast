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
package org.factcast.server.grpc.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.NonNull;
import lombok.experimental.Delegate;

public class FactCastUser implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;

    @Delegate
    private final User user;

    private final FactCastAccount account;

    public FactCastUser(FactCastAccount account, String secret) {
        this.account = account;
        this.user = new User(account.id(), secret, AuthorityUtils.createAuthorityList(
                FactCastAuthority.AUTHENTICATED));
    }

    private final Map<String, Boolean> readAccess = new HashMap<>();

    private final Map<String, Boolean> writeAccess = new HashMap<>();

    public boolean canRead(@NonNull String ns) {
        return readAccess.computeIfAbsent(ns, account::canRead);
    }

    public boolean canWrite(@NonNull String ns) {
        return writeAccess.computeIfAbsent(ns, account::canWrite);
    }

}
