package org.factcast.store.pgsql.validation.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.factcast.store.pgsql.validation.schema.SchemaKey;
import org.factcast.store.pgsql.validation.schema.SchemaSource;
import org.factcast.store.pgsql.validation.schema.SchemaStore;
import org.factcast.store.pgsql.validation.schema.store.InMemSchemaStoreImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;

public class HttpSchemaRegistryTest {

	@Test
	public void testRoundtrip() throws InterruptedException, ExecutionException, IOException {

		URL url = new URL("http://some.funky/registry/");

		IndexFetcher indexFetcher = mock(IndexFetcher.class);
		SchemaFetcher schemafetcher = mock(SchemaFetcher.class);

		RegistryIndex index = new RegistryIndex();
		SchemaSource source1 = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
		SchemaSource source2 = new SchemaSource("http://foo/2", "123", "ns", "type", 2);
		index.schemes(Lists.newArrayList(source1, source2));
		when(indexFetcher.fetchIndex()).thenReturn(Optional.of(index));

		when(schemafetcher.fetch(any())).thenReturn("{}");

		SchemaStore store = spy(new InMemSchemaStoreImpl());
		HttpSchemaRegistry uut = new HttpSchemaRegistry(store, indexFetcher, schemafetcher);
		uut.refresh();

		verify(store, times(2)).register(Mockito.any(), Mockito.any());

		assertTrue(store.get(SchemaKey.builder().ns("ns").type("type").version(1).build()).isPresent());
		assertTrue(store.get(SchemaKey.builder().ns("ns").type("type").version(2).build()).isPresent());
		assertFalse(store.get(SchemaKey.builder().ns("ns").type("type").version(3).build()).isPresent());

	}
}
