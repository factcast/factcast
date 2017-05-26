package org.factcast.store.pgsql;

public enum CatchupStrategy {

    PAGED, QUEUED;

    public static CatchupStrategy getDefault() {
        return PAGED;
    }

}
