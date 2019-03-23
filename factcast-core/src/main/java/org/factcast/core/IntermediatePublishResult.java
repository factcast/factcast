package org.factcast.core;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor(staticName = "of")
@Data
@Accessors(fluent = true, chain = true)
public class IntermediatePublishResult {
    @NonNull
    final List<Fact> factsToPublish;

    Function<UUID, PublishResult> andThen = (lastFactId) -> PublishResult.ok(lastFactId);

    public static IntermediatePublishResult of(Fact f) {
        return of(Arrays.asList(f));
    }

}
