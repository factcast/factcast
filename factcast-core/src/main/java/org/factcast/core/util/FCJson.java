package org.factcast.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.NonNull;

/**
 * Statically shared ObjectMapper reader & writer to be used within FactCast for
 * Headers and FactCast-specific objects.
 * 
 * You must not change the configuration of this mapper, and it should not be
 * used outside of FactCast.
 * 
 * @author usr
 *
 */

public class FCJson {
	private final static ObjectMapper objectMapper = new ObjectMapper();

	private static final ObjectReader reader;

	private static final ObjectWriter writer;

	static {
		writer = objectMapper.writer();
		reader = objectMapper.reader();
	}

	@NonNull
	public static ObjectReader reader() {
		return reader;
	}

	@NonNull

	public static ObjectWriter writer() {
		return writer;
	}
}
