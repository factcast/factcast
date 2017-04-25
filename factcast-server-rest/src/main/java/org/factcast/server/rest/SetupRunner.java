package org.factcast.server.rest;

import java.util.Arrays;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.NonNull;

//TODO move that in test folder
@Component
public class SetupRunner implements CommandLineRunner {
	private static final Fact one = new Fact() {

		private final UUID id = UUID.randomUUID();
		private final UUID aggId = UUID.randomUUID();

		@Override
		public String type() {
			return "a";
		}

		@Override
		public String ns() {
			return "a";
		}

		@Override
		public String jsonPayload() {
			return "{}";
		}

		@Override
		public String jsonHeader() {

			return "{\"ns\":\"a\","//
					+ "\"type\":\"a\","//
					+ "\"aggId\":\"" + aggId + "\","//
					+ "\"id\":\"" + id + "\""//
					+ "}";
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public UUID aggId() {
			return aggId;
		}

		@Override
		public String meta(String key) {
			return null;
		}
	};

	private final FactStore factStore;

	@Autowired
	public SetupRunner(@NonNull FactStore factStore) {
		this.factStore = factStore;
	}

	@Override
	public void run(String... arg0) throws Exception {
		factStore.publish(Arrays.asList(one));

	}

}
