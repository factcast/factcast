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
package org.factcast.grpc.api;

import io.grpc.Metadata;

public class Headers {

  private static final String GRPC_COMPRESSION_HEADER = "fc-msgcomp";

  private static final String GRPC_CATCHUP_BATCHSIZE = "fc-cbat";

  // questionable, should be part of request?
  private static final String GRPC_FAST_FORWARD = "fc-ffwd";

  private static final String GRPC_CLIENT_ID = "fc-id";
  private static final String GRPC_CLIENT_VERSION = "fc-version";

  public static final Metadata.Key<String> MESSAGE_COMPRESSION =
      Metadata.Key.of(Headers.GRPC_COMPRESSION_HEADER, Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> CATCHUP_BATCHSIZE =
      Metadata.Key.of(Headers.GRPC_CATCHUP_BATCHSIZE, Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> FAST_FORWARD =
      Metadata.Key.of(Headers.GRPC_FAST_FORWARD, Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> CLIENT_ID =
      Metadata.Key.of(Headers.GRPC_CLIENT_ID, Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> CLIENT_VERSION =
      Metadata.Key.of(Headers.GRPC_CLIENT_VERSION, Metadata.ASCII_STRING_MARSHALLER);
}
