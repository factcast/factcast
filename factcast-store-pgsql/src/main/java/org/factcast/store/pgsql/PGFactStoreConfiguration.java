package org.factcast.store.pgsql;

import org.factcast.store.pgsql.internal.PGFactStoreInternalConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(PGFactStoreInternalConfiguration.class)
public class PGFactStoreConfiguration {

}
