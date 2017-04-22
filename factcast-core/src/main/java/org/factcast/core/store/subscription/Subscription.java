package org.factcast.core.store.subscription;

/**
 * A handle that is returned from Subscribe Methods in order to be able to
 * signal, that a client lost its interest in the stream.
 * 
 * This might be used for resource-efficiency
 * 
 * @author usr
 *
 */
public interface Subscription extends AutoCloseable {

}
