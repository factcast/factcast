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
package org.factcast.client.grpc.cli.cmd;

import com.beust.jcommander.*;
import com.beust.jcommander.converters.CommaParameterSplitter;
import java.util.*;
import org.factcast.client.grpc.cli.util.*;
import org.factcast.core.FactCast;

@SuppressWarnings({"all"})
@Parameters(
    commandNames = "serialOf",
    commandDescription = "get the serial of a fact identified by id")
public class SerialOf implements Command {

  @Parameter(required = true, description = "id", splitter = CommaParameterSplitter.class)
  List<UUID> ids = new LinkedList<>();

  @Override
  public void runWith(FactCast fc, Options opt) {
    ids.forEach(
        id -> {
          System.out.print(id + ": ");
          OptionalLong serial = fc.serialOf(id);
          if (serial.isPresent()) {
            System.out.println(serial.getAsLong());
          } else {
            System.out.println("not found");
          }
        });
  }
}
