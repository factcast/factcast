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
package org.factcast.client.grpc.spring;

import static org.factcast.core.TestHelper.expectNPE;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

@ExtendWith(MockitoExtension.class)
public class SpringGrpcFactStoreTest {

    @InjectMocks
    private SpringGrpcFactStore uut;

    @Test
    void testConstruction() {
        expectNPE(() -> new SpringGrpcFactStore((AddressChannelFactory) null));
    }

    @Test
    public void testAfterSingletonsInstantiatedCallsInit() throws Exception {
        uut = spy(uut);
        uut.afterSingletonsInstantiated();
        verify(uut).initialize();
    }
}
