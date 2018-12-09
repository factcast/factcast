package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.IdObserver;
import org.factcast.store.test.AbstractFactStoreTest;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PGFactStoreTest extends AbstractFactStoreTest {

    @Autowired
    FactStore store;

    @Override
    protected FactStore createStoreToTest() {
        return store;
    }

}
