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
package org.factcast.core.subscription.observer;

import lombok.Generated;

/**
 * Callback interface to use when subscribing to Facts from FactCast on the Server Side only. This
 * thing re-adds onNext(Fact) so that it can be used on the server side without wrapping in
 * Collections.singletonList
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Generated // sneakily skip coverage generation
public interface ServerSideFactObserver extends BatchingFactObserver, FactObserver {}
