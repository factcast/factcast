package org.factcast.store.pgsql.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.core.wellknown.MarkFact;
import org.factcast.store.pgsql.PGFactStoreConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PGFactStoreConfiguration.class })
@Sql(scripts = { "/test_schema.sql" }, config = @SqlConfig(separator = "#"))
// TODO move to own module
public class PGSqlFactStoreMT {

	static {
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.INFO);
	}

	@Autowired
	FactStore store;

	@Test
	public void testMachineGun() throws Exception {

		if (Boolean.getBoolean("runPerformanceTests")) {

			MarkFact startMark = new MarkFact();
			store.publish(startMark);

			LinkedList<List<Fact>> l = new LinkedList<>();
			for (int i = 0; i < 1000; i++) {
				LinkedList<Fact> b = new LinkedList<>();
				for (int j = 0; j < 100; j++) {
					b.add(new MarkFact());
				}
				l.add(b);
			}

			long start = System.currentTimeMillis();
			l.forEach(store::publish);
			long end = System.currentTimeMillis();
			System.out.println("W rt=" + (end - start) + "ms");

			start = System.currentTimeMillis();
			AtomicInteger found = new AtomicInteger(0);
			SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("default-ns")).since(startMark.id());
			store.subscribe(req, f -> {
				found.incrementAndGet();
			}).get();
			// while (found.get() < 100000) {
			// Thread.sleep(50);
			// }
			end = System.currentTimeMillis();
			System.out.println("R " + found.get() + " rt=" + (end - start) + "ms");
		}
	}
}
