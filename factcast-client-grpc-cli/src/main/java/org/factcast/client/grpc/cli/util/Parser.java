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
package org.factcast.client.grpc.cli.util;

import java.util.Arrays;
import java.util.List;

import org.factcast.client.grpc.cli.conv.Converters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.JCommander.Builder;

import lombok.Getter;

public class Parser {

    private static final String HOST_SYSPROP_NAME = "grpc.client.factstore.host";

    private static final String PORT_SYSPROP_NAME = "grpc.client.factstore.port";

    private static final String NEGOTIATION_SYSPROP_NAME = "grpc.client.factstore.negotiationType";

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
        System.setProperty(HOST_SYSPROP_NAME, options.host);
        System.setProperty(PORT_SYSPROP_NAME, String.valueOf(options.port));
        if (Boolean.valueOf(options.debug)) {
            System.setProperty("debug", Boolean.TRUE.toString());
        }
        if (Boolean.valueOf(options.notls)) {
            System.setProperty(NEGOTIATION_SYSPROP_NAME, "plaintext");
        }
    }

    public void usage() {
        jc.usage();
    }
}
