package org.factcast.core.subscription;

import org.factcast.core.Fact;

/**
 * an observer that provides Facts.
 * 
 * see {@link GenericObserver}.
 * 
 * @author usr
 *
 */
public interface FactObserver extends GenericObserver<Fact> {
}