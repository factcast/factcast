package org.factcast.store.pgsql.validation.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;

import org.junit.jupiter.api.Test;

public class IndexFetcherTest {

	private IndexFetcher uut;

	@Test
	void testFetch() throws Exception {
		uut = new IndexFetcher(
				new URL("http://factcast-schema-registry-test.s3-website.eu-central-1.amazonaws.com/registry"));
		assertTrue(uut.fetchIndex().isPresent());
		assertFalse(uut.fetchIndex().isPresent());
		assertFalse(uut.fetchIndex().isPresent());

	}

}
