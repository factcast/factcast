package org.factcast.server.grpc;

public class RequestCanceledByClientException extends RuntimeException {
  private static final long serialVersionUID = 4963973755426184988L;

  public RequestCanceledByClientException(String s) {
    super(s);
  }
}
