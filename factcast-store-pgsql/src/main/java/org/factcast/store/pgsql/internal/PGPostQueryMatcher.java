package org.factcast.store.pgsql.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecMatcher;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Predicate to filter Facts selected by the database query.
 * 
 * For PG, we can safely assume that only those rows are returned from the DB,
 * that match the queryable criteria. The only untested thing is the
 * script-match which can be skipped, if no FactSpec has a scripted filter.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j
class PGPostQueryMatcher implements Predicate<Fact> {

    final boolean canBeSkipped;

    final List<FactSpecMatcher> matchers = new LinkedList<>();

    public PGPostQueryMatcher(@NonNull List<FactSpec> specs) {
        canBeSkipped = !specs.stream().anyMatch(s -> s.jsFilterScript() != null);
        if (canBeSkipped) {
            log.trace("post query filtering has been disabled");
        } else {
            this.matchers.addAll(specs.stream().map(s -> new FactSpecMatcher(s)).collect(Collectors
                    .toList()));
        }
    }

    @Override
    public boolean test(Fact input) {
        return canBeSkipped || matchers.stream().anyMatch(m -> m.test(input));
    }

}
