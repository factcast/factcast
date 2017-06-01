package org.factcast.store.pgsql;

/**
 * 
 * Defines the catchup-Strategy to use, as well as the default, if none is
 * specified.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
public enum CatchupStrategy {

    PAGED, QUEUED;

    public static CatchupStrategy getDefault() {
        return PAGED;
    }

}
