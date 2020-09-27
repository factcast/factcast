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
package org.factcast.client.grpc.cli.util;

import com.beust.jcommander.Parameter;

public class Options {

  @Parameter(
      names = {"--help", "-help", "-?", "--?"},
      help = true,
      hidden = true)
  boolean help;

  @Parameter(
      names = {
        "--basic", "-basic",
      },
      help = true,
      description = "Basic-Auth crendentials in the form \"user:password\"")
  String basicAuthCredentials;

  @Parameter(
      names = {"--pretty"},
      help = true,
      description = "format JSON output")
  boolean pretty = false;

  @Parameter(
      names = {"--no-tls"},
      help = true,
      description = "do NOT use TLS to connect (plaintext-communication)")
  boolean notls = false;

  @Parameter(
      names = {"--debug"},
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
