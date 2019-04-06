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
package org.factcast.store.pgsql.rds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.aws.jdbc.rds.AmazonRdsDataSourceFactoryBean;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
public class RdsDataSourceFactoryBeanPostProcessorTest {
    @Mock
    Environment env;

    @InjectMocks
    RdsDataSourceFactoryBeanPostProcessor uut;

    @Test
    public void shouldReturnFromAfterInitSameBeanAsPassed() throws Exception {
        UUID instance = UUID.randomUUID();

        Object afterInit = uut.postProcessAfterInitialization(instance, "foo");

        assertThat(afterInit).isSameAs(instance);
    }

    @Test
    public void shouldReturnFromBeforeSameBeanAsPassed() throws Exception {
        UUID instance = UUID.randomUUID();

        Object beforeInit = uut.postProcessBeforeInitialization(instance, "foo");

        assertThat(beforeInit).isSameAs(instance);
    }

    @Test
    public void shouldReplaceJdbcCP() throws Exception {
        AmazonRdsDataSourceFactoryBean instance = mock(AmazonRdsDataSourceFactoryBean.class);
        when(env.getProperty(any(), any(), anyBoolean())).thenReturn(true);

        AmazonRdsDataSourceFactoryBean afterInit = (AmazonRdsDataSourceFactoryBean) uut
                .postProcessBeforeInitialization(instance, "foo");

        assertThat(afterInit).isSameAs(instance);
        verify(instance).setDataSourceFactory(any());
    }

}
