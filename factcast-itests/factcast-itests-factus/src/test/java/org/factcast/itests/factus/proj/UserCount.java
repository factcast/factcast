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
package org.factcast.itests.factus.proj;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.springframework.stereotype.Component;

@Component
public class UserCount extends LocalManagedProjection {

    private int users = 0;

    @Handler
    void apply(UserCreated created) {
        users++;
    }

    @Handler
    void apply(UserDeleted deleted) {
        users--;
    }

    public int count() {
        return users;
    }

}
