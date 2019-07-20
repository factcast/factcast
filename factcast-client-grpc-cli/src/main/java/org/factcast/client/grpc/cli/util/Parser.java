/*
 * Copyright © 2019 factcast
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
package org.factcast.client.grpc.cli.util;

import java.util.Arrays;
import java.util.List;

import org.factcast.client.grpc.cli.conv.Converters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.JCommander.Builder;
import com.beust.jcommander.Parameter;

import lombok.Getter;

@SuppressWarnings("ALL")
public class Parser {

    private static final String NEGOTIATION_SYSPROP_NAME = "grpc.client.factstore.negotiationType";

    private static final String BASICAUTH_SYSPROP_NAME = "grpc.client.factstore.credentials";

    private static final String ADDRESS_SYSPROP_NAME = "grpc.client.factstore.address";

    private final JCommander jc;

    @Getter
    final Options options = new Options();

    public Parser(Command... commands) {
        Builder builder = JCommander.newBuilder().addConverterInstanceFactory(Converters.factory());
        builder.addObject(options);
        builder.programName("fc-cli");
        Arrays.asList(commands).forEach(builder::addCommand);
        this.jc = builder.build();
    }

    public Command parse(String[] args) {
        jc.parse(args);

        JCommander parsedCommand = jc.getCommands().get(jc.getParsedCommand());
        if (parsedCommand != null) {
            init();
            List<Object> objects = parsedCommand.getObjects();
            return (Command) objects.get(0);
        }
        jc.usage();
        return null;
    }

    private void init() {
        System.setProperty(ADDRESS_SYSPROP_NAME, options.address);

        if (options.debug) {
            System.setProperty("debug", Boolean.TRUE.toString());
        }
        if (options.notls) {
            System.setProperty(NEGOTIATION_SYSPROP_NAME, "plaintext");
        }
        if (options.basicAuthCredentials != null) {
            System.setProperty(BASICAUTH_SYSPROP_NAME, options.basicAuthCredentials);
        }
    }

    public static class Options {

        @Parameter(names = { "--help", "-help", "-?", "--?" }, help = true, hidden = true)
        boolean help;

        @Parameter(
                names = { "--basic", "-basic", },
                help = true,
                hidden = false,
                description = "Basic-Auth Crendentials in the form \"user:password\"")
        String basicAuthCredentials;

        @Getter
        @Parameter(names = { "--pretty" }, help = true, description = "format JSON output")
        boolean pretty = false;

        @Getter
        @Parameter(
                names = {
                        "--no-tls" },
                help = true,
                description = "do NOT use TLS to connect (plaintext-communication)")
        boolean notls = false;

        @Getter
        @Parameter(
                names = { "--debug" },
                help = true,
                description = "show debug-level debug messages",
                order = 0)
        boolean debug = false;

        @Parameter(names = "--address", description = "the address to connect to", order = 1)
        String address = "static://localhost:9090";

        public Options() {
            String fc = System.getenv("FACTCAST_SERVER");
            if (fc != null) {
                address = fc;
            }
        }
    }

    public void usage() {
        jc.usage();
    }
}
