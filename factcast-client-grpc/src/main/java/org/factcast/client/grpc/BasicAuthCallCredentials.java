package org.factcast.client.grpc;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;

@RequiredArgsConstructor
public class BasicAuthCallCredentials extends CallCredentials {

  private final String authorization;

  public static BasicAuthCallCredentials of(@NonNull String username, @NonNull String password) {
    String auth = username + ":" + password;
    byte[] encoded;
    try {
      encoded = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Failed to encode basic authentication token", e);
    }
    String var10000 = new String(encoded, StandardCharsets.UTF_8);
    return new BasicAuthCallCredentials("Basic " + var10000);
  }

  @Override
  public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
    Metadata extraHeaders = new Metadata();
    extraHeaders.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), authorization);
    metadataApplier.apply(extraHeaders);
  }
}
