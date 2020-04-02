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
package org.factcast.store.pgsql.validation;

import java.util.Timer;
import java.util.TimerTask;

import org.factcast.store.pgsql.validation.schema.SchemaRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import lombok.Value;

@Value
public class ScheduledRegistryRefresher implements SmartInitializingSingleton, DisposableBean {

    final SchemaRegistry registry;

    final long rate;

    final Timer timer = new Timer(true);

    @Override
    public void afterSingletonsInstantiated() {
        TimerTask t = new TimerTask() {

            @Override
            public void run() {
                registry.refreshSilent();
            }
        };

        long delay = rate / 2;
        timer.scheduleAtFixedRate(t, delay, rate);
    }

    @Override
    public void destroy() throws Exception {
        timer.cancel();
    }
}
