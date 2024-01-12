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
package org.factcast.spring.boot.autoconfigure.factus;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.SnapshotSerializerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
@Slf4j
public class SpringSnapshotSerializerFactory implements SnapshotSerializerFactory {
  @NonNull final ApplicationContext ctx;
  @NonNull final SnapshotSerializerFactory wrappedFactory;

  @Override
  public <T extends SnapshotSerializer> T create(Class<T> type) {
    try {
      // prefer bean if it exists
      return ctx.getBean(type);
    } catch (NoSuchBeanDefinitionException ex) {
      // not worth a warning
      log.debug(
          "While looking for bean type {}. Falling back to ",
          wrappedFactory.getClass().getCanonicalName());
    } catch (Exception ex) {
      log.warn(
          "While looking for bean type {}. Falling back to ",
          wrappedFactory.getClass().getCanonicalName());
    }
    return wrappedFactory.create(type);
  }
}
