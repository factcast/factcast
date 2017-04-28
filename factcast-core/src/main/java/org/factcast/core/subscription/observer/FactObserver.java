package org.factcast.core.subscription.observer;

import org.factcast.core.Fact;

/**
 * an observer that provides Facts.
 * 
 * see {@link GenericObserver}.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
public interface FactObserver extends GenericObserver<Fact> {
}