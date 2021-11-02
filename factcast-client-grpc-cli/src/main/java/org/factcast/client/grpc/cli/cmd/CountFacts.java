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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.factcast.client.grpc.cli.util.Command;
import org.factcast.client.grpc.cli.util.Options;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Lists;

@Parameters(
    commandNames = "countFacts",
    commandDescription = "Count all facts, using given factspecs")
public class CountFacts implements Command {

  @Parameter(
      names = "-ns",
      description = "the namespace filtered on. This is required if type, aggId or meta is set.")
  String ns;

  @Parameter(names = "-t", description = "the type filtered on")
  String type;

  @Parameter(names = "-aggId", description = "the aggId filtered on")
  String aggId;

  @Parameter(
      names = "-meta",
      description =
          "the meta info filtered on; must be key-value pairs in the form 'key1=value1,key2=value2,...'")
  String meta;

  @Override
  public void runWith(FactCast fc, Options opt) {
    List<FactSpec> specs = getSpecs();

    System.out.println("Number of matching facts: " + fc.countFacts(specs));
  }

  private List<FactSpec> getSpecs() {
    if (ns == null && type == null && aggId == null && meta == null) {
      return Collections.emptyList();
    }

    if (ns == null) {
      throw new IllegalArgumentException("ns cannot be null when type, aggId or meta is given!");
    }

    FactSpec factSpec = FactSpec.ns(ns).type(type).aggId(aggId == null ? null : UUID.fromString(aggId));
    if (meta != null) {
      for (String pair : meta.split("\\s*,\\s*")) {
        String[] keyValue = pair.split("\\s*=\\s*");
        if (keyValue.length != 2) {
          throw new IllegalArgumentException(
              "meta not in form key1=value1,key2=value2,... - aborting!");
        }
        factSpec.meta(keyValue[0], keyValue[1]);
      }
    }
    return Lists.newArrayList(factSpec);
  }
}
