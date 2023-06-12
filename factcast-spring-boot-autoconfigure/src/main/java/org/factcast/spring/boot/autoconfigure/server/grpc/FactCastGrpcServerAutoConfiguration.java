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
package org.factcast.spring.boot.autoconfigure.server.grpc;

import lombok.Generated;
import net.devh.boot.grpc.server.autoconfigure.*;
import org.factcast.server.grpc.FactCastGrpcServerConfiguration;
import org.factcast.server.grpc.FactCastSecurityConfiguration;
import org.factcast.server.grpc.FactStoreGrpcService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Import;

@Generated
@AutoConfiguration
@Import({FactCastGrpcServerConfiguration.class, FactCastSecurityConfiguration.class})
@ImportAutoConfiguration({
  GrpcAdviceAutoConfiguration.class,
  GrpcHealthServiceAutoConfiguration.class,
  GrpcMetadataConsulConfiguration.class,
  GrpcMetadataEurekaConfiguration.class,
  GrpcMetadataNacosConfiguration.class,
  GrpcMetadataZookeeperConfiguration.class,
  GrpcReflectionServiceAutoConfiguration.class,
  GrpcServerAutoConfiguration.class,
  GrpcServerFactoryAutoConfiguration.class,
  GrpcServerMetricAutoConfiguration.class,
  GrpcServerSecurityAutoConfiguration.class,
  GrpcServerTraceAutoConfiguration.class
})
@ConditionalOnClass(FactStoreGrpcService.class)
@ConditionalOnMissingBean(FactStoreGrpcService.class)
@AutoConfigureBefore(GrpcServerAutoConfiguration.class)
public class FactCastGrpcServerAutoConfiguration {}
