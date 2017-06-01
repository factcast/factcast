package org.factcast.server.grpc;

/**
 * Signals an uncatched Exception in the Transport layer, normally leading to a
 * subscription being cancelled.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
class TransportLayerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TransportLayerException(String msg) {
    }

}
