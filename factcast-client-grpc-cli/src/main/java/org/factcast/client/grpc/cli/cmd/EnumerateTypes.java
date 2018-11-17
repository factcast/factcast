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

import org.factcast.client.grpc.cli.util.Command;
import org.factcast.client.grpc.cli.util.Parser.Options;
import org.factcast.core.FactCast;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(
        commandNames = "enumerateTypes",
        commandDescription = "lists all types used with a namespace in no particular order")
public class EnumerateTypes implements Command {

    @Parameter(required = true, description = "namespace")
    String ns;

    @Override
    public void runWith(FactCast fc, Options opt) {
        fc.enumerateTypes(ns).forEach(System.out::println);
    }
}
