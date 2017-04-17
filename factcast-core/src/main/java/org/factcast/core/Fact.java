package org.factcast.core;

import java.util.UUID;

import lombok.NonNull;

/**
 * Defines a fact to be either published or consumed. Consists of two JSON
 * Strings: jsonHeader and jsonPayload. Also provides convenience getters for
 * id,ns,type and aggId.
 *
 * Only generated code, does not need unit testing.
 *
 * @author usr
 *
 */
public interface Fact {

	@NonNull
	UUID id();

	@NonNull
	String ns();

	String type();

	UUID aggId();

	@NonNull
	String jsonHeader();

	@NonNull
	String jsonPayload();
	// TODO add schema

	String meta(String key);
}
