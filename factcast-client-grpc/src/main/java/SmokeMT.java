import org.factcast.client.grpc.GrpcFactStoreAdapter;
import org.factcast.core.FactObserver;
import org.factcast.core.IdObserver;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.server.grpc.api.RemoteFactStore;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class SmokeMT {
	public static void main(String[] args) {
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);

		RemoteFactStore adapter = new GrpcFactStoreAdapter();
		// Optional<Fact> fetchById = adapter.fetchById(UUID.randomUUID());
		// System.out.println(fetchById.isPresent());
		//
		// TestFact f1 = new TestFact().type("create");
		// adapter.publish(f1);
		//
		// fetchById = adapter.fetchById(f1.id());
		// System.out.println(fetchById.isPresent());
		//
		// TestFact m = new TestFact().type("withMark");
		// UUID mark = adapter.publishWithMark(m);
		//
		// System.out.println(adapter.fetchById(m.id).isPresent());
		// System.out.println(adapter.fetchById(mark).isPresent());
		//

		adapter.subscribe(SubscriptionRequest.catchup(FactSpec.ns("default")).asIds().sinceInception(),
				(IdObserver) f -> {
					System.err.println("csubId " + f);

				});

		adapter.subscribe(SubscriptionRequest.catchup(FactSpec.ns("default")).asFacts().sinceInception(),
				(FactObserver) f -> {
					System.err.println("csubFact " + f);
				});

	}
}
