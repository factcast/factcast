/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.factus.redis.tx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.factcast.factus.redis.AbstractRedisSubscribedProjection;

/**
 * Only works on subclasses of {@link org.factcast.factus.redis.AbstractRedisSubscribedProjection}.
 *
 * <p>Enables that the methods {@link AbstractRedisSubscribedProjection#onCommit()} and {@link
 * AbstractRedisSubscribedProjection#onRollback()} will be invoked when the tx manager commits or
 * rolls back.
 *
 * <p>Attention, in case you are adding this to projections that are already deployed, it would be
 * safer to increase the serial of the projection. Otherwise, during the first deployment, some
 * updates might get lost when the instance that processes does not have this annotation, and the
 * newly deployed instances do not have the lock but expect the callbacks.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface EnableRedisTransactionCallbacks {}
