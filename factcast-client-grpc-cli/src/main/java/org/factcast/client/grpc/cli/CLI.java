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
package org.factcast.client.grpc.cli;

import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.client.grpc.cli.cmd.Catchup;
import org.factcast.client.grpc.cli.cmd.EnumerateNamespaces;
import org.factcast.client.grpc.cli.cmd.EnumerateTypes;
import org.factcast.client.grpc.cli.cmd.Fetch;
import org.factcast.client.grpc.cli.cmd.Follow;
import org.factcast.client.grpc.cli.cmd.Publish;
import org.factcast.client.grpc.cli.cmd.SerialOf;
import org.factcast.client.grpc.cli.util.Command;
import org.factcast.client.grpc.cli.util.Options;
import org.factcast.client.grpc.cli.util.Parser;
import org.factcast.core.FactCast;

import com.beust.jcommander.ParameterException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor

public class CLI {

    public static void main(String[] args) {
        String[] arguments = args;
        if (arguments == null || arguments.length == 0)
            arguments = new String[] { "--help" };
        Parser parser = new Parser(new Catchup(), new Follow(), new Publish(), new Fetch(),
                new EnumerateNamespaces(),
                new EnumerateTypes(), new SerialOf());
        try {
            Command cmd = parser.parse(arguments);
            Options options = parser.options();

            ManagedChannelBuilder<?> cb = ManagedChannelBuilder.forAddress(options.host(),
                    Integer.valueOf(options.port()));
            if (Boolean.valueOf(options.notls()))
                cb.usePlaintext();

            ManagedChannel channel = cb.build();

            GrpcFactStore store = new GrpcFactStore(channel);
            store.initialize();
            FactCast fc = FactCast.from(store);
            cmd.runWith(fc, options);
        } catch (ParameterException e) {
            System.err.println();
            System.err.println("*** Error: " + e.getMessage());
            System.err.println();
            parser.usage();
            System.exit(1);
        }
    }
}
