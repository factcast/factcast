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

import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.annotations.VisibleForTesting;

import lombok.Generated;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

/**
 * Adapter that implements a FactStore by calling a remote one via GRPC.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
public class SpringGrpcFactStore extends GrpcFactStore
        implements SmartInitializingSingleton {

    static final String CHANNEL_NAME = "factstore";

    @Autowired
    @Generated
    SpringGrpcFactStore(AddressChannelFactory channelFactory) {
        super(channelFactory.createChannel(CHANNEL_NAME));
    }

    @VisibleForTesting
    @lombok.Generated
    SpringGrpcFactStore(@NonNull RemoteFactStoreBlockingStub newBlockingStub,
            @NonNull RemoteFactStoreStub newStub) {
        super(newBlockingStub, newStub);
    }

    public synchronized void afterSingletonsInstantiated() {
        initialize();
    }

}
