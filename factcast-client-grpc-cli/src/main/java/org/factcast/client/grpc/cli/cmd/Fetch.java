/**
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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.factcast.client.grpc.cli.util.Command;
import org.factcast.client.grpc.cli.util.FactRenderer;
import org.factcast.client.grpc.cli.util.Parser.Options;
import org.factcast.core.FactCast;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.CommaParameterSplitter;

@Parameters(commandNames = "fetch", commandDescription = "fetch a particular fact by id")
public class Fetch implements Command {

    @SuppressWarnings("DefaultAnnotationParam")
    @Parameter(required = true, description = "id", splitter = CommaParameterSplitter.class)
    List<UUID> ids = new LinkedList<>();

    @Override
    public void runWith(FactCast fc, Options opt) {
        FactRenderer factRenderer = new FactRenderer(opt);
        ids.forEach(id -> System.out.println(fc.fetchById(id).map(factRenderer::render).orElse(
                "not found")));
    }
}
