package org.factcast.server.rest.util;

import java.util.function.Supplier;

public class SetableSupplier<T> implements Supplier<T> {

	private T obj;

	@Override
	public T get() {
		return obj;
	}

	public void set(T obj) {
		this.obj = obj;
	}
}
