package org.factcast.core.lock;

import java.util.List;
import java.util.Optional;

import org.factcast.core.Fact;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class IntermediatePublishResult {

    @Getter
    final List<Fact> factsToPublish;

    @Setter
    private Runnable andThen = null;

    public Optional<Runnable> andThen() {
        return Optional.ofNullable(andThen);
    }

}