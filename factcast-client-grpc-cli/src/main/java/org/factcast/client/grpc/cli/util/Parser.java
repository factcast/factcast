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
package org.factcast.client.grpc.cli.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.factcast.client.grpc.cli.conv.Converters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.JCommander.Builder;
import com.beust.jcommander.Parameter;

import lombok.Getter;

public class Parser {

    private static final String HOST_SYSPROP_NAME = "grpc.client.factstore.host";

    private static final String PORT_SYSPROP_NAME = "grpc.client.factstore.port";

    final JCommander jc;

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
        if (options.help) {
            jc.usage();
            return null;
        }
        init();
        List<Object> objects = jc.getCommands().get(jc.getParsedCommand()).getObjects();
        return (Command) objects.get(0);
    }

    private void init() {
        System.setProperty(HOST_SYSPROP_NAME, options.host);
        System.setProperty(PORT_SYSPROP_NAME, String.valueOf(options.port));
        if (options.debug) {
            System.setProperty("debug", Boolean.TRUE.toString());
        }
    }

    public static class Options {

        @Parameter(names = { "--help", "-help", "-?", "--?" }, help = true, hidden = true)
        boolean help;

        @Getter
        @Parameter(names = { "--pretty" }, help = true, description = "format JSON output")
        boolean pretty = false;

        @Getter
        @Parameter(
                names = { "--debug" },
                help = true,
                description = "show debug-level debug messages",
                order = 0)
        boolean debug = false;

        @Parameter(names = "--host", description = "the hostname to connect to", order = 1)
        String host = "localhost";

        @Parameter(names = "--port", description = "the port to connect to", order = 2)
        int port = 9090;

        public Options() {
            String fc = System.getenv("FACTCAST_SERVER");
            if (fc != null) {
                Iterator<String> i = Arrays.asList(fc.split(":")).iterator();
                if (i.hasNext()) {
                    String h = i.next();
                    if (h != null && h.trim().length() > 0)
                        host = h;
                }
                if (i.hasNext()) {
                    String p = i.next();
                    if (p != null && p.trim().length() > 0)
                        port = Integer.parseInt(p);
                }
            }
        }
    }

    public void usage() {
        jc.usage();
    }
}
