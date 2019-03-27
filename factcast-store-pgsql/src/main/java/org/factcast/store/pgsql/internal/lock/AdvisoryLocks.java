package org.factcast.store.pgsql.internal.lock;

import lombok.Getter;

public enum AdvisoryLocks {
    PUBLISH(128);
    @Getter
    private int code;

    private AdvisoryLocks(int i) {
        this.code = i;
    }

}
