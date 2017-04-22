package org.factcast.store.pgsql;

import org.factcast.store.pgsql.internal.PGFactStoreInternalConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration to include in order to use a PGFactStore
 * 
 * just forwards to {@link PGFactStoreInternalConfiguration}, so that IDEs can
 * still complain about internal references.
 * 
 * @author usr
 *
 */
@Configuration
@Import(PGFactStoreInternalConfiguration.class)
public class PGFactStoreConfiguration {

}
