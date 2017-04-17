package org.factcast.server.rest.resources;

import lombok.NonNull;
import lombok.Value;

@Value
public class EventIdJson {

	@NonNull
	private String id;
}
