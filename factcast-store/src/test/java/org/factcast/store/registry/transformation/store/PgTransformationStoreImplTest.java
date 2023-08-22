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
package org.factcast.store.registry.transformation.store;

import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@IntegrationTest
class PgTransformationStoreImplTest extends AbstractTransformationStoreTest {
  @Autowired private JdbcTemplate jdbcTpl;
  @Autowired private TransactionTemplate txTpl;
  @Autowired private StoreConfigurationProperties props;

  @Override
  protected TransformationStore createUUT() {
    return new PgTransformationStoreImpl(jdbcTpl, txTpl, registryMetrics, props);
  }
}
