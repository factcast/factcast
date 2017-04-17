package org.factcast.core;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;


@RequiredArgsConstructor
public class DefaultFactFactory {
	private final ObjectMapper jackson;

	public Fact create(@NonNull Fact f) {
		return create(f.jsonHeader(), f.jsonPayload());
	}

	@SneakyThrows
	public Fact create(@NonNull String header, @NonNull String payload) {
		return new DefaultFactImpl(header, payload, jackson);
	}
}
