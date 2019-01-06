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
package org.factcast.client.grpc.cli.cmd;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.factcast.client.grpc.cli.util.ConsoleFactObserver;
import org.factcast.client.grpc.cli.util.Parser.Options;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.GenericObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class CatchupTest {
    @Mock
    FactStore fs;

    FactCast fc;

    @Test
    void testCatchup() throws Exception {
        String ns = "foo";
        UUID startId = new UUID(0, 1);
        Catchup cmd = new Catchup(ns, startId);

        fc = spy(FactCast.from(fs));
        Options opt = new Options();

        when(fs.subscribe(any(), any(ConsoleFactObserver.class))).thenAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                GenericObserver<?> o = (GenericObserver<?>) args[1];
                o.onCatchup();
                o.onComplete();

                SubscriptionRequest r = (SubscriptionRequest) args[0];

                List<FactSpec> specs = new ArrayList<FactSpec>(r.specs());
                specs.remove(FactSpec.forMark());

                assertEquals(startId, r.startingAfter().get());
                assertEquals(ns, specs.iterator().next().ns());

                return null;
            }
        });
        cmd.runWith(fc, opt);

        verify(fc).subscribeToFacts(any(SubscriptionRequest.class), any(ConsoleFactObserver.class));
    }
}
