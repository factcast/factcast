package org.factcast.factus.metrics;

import lombok.Getter;
import lombok.NonNull;

public enum GaugedEvent {
    FETCH_SIZE("fetch_size");

    @NonNull
    @Getter
    final String event;

    GaugedEvent(@NonNull String event) {
        this.event = event;
    }
}
