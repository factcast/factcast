package org.factcast.store.pgsql.validation.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import okhttp3.HttpUrl;

@ExtendWith(MockitoExtension.class)
public class SchemaRegistryUnavailableExceptionTest {

	@Test
	public void testSchemaRegistryUnavailableExceptionHttpUrlIntString() throws Exception {
		HttpUrl url = new HttpUrl.Builder().scheme("https").host("www.google.com").addPathSegment("search")
				.addQueryParameter("q", "polar bears").build();
		SchemaRegistryUnavailableException uut = new SchemaRegistryUnavailableException(url, 403, "damnit");

		assertThat(uut.getMessage()).contains("403").contains("damnit").contains("bears");
	}

}
