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
package org.factcast.example.tls.client;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

@SuppressWarnings("ALL")
@SpringBootApplication
public class TLSClient {

    public static void main(String[] args) {
        try (
                DockerComposeContainer<?> compose = new DockerComposeContainer(
                        new File("docker-compose.yml"))
                                .withExposedService("db", 5432, new HostPortWaitStrategy())
                                .withExposedService("factcast", 9443,
                                        new HostPortWaitStrategy());) {
            compose.start();
            SpringApplication.run(TLSClient.class, args);
        }
    }
}
