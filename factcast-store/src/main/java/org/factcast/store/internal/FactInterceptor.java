package org.factcast.store.internal;

import java.util.function.*;

import org.factcast.core.Fact;
/** facts might be filtered away or transformed before passing along to the target subscription */
public interface FactInterceptor extends Consumer<Fact> {}
