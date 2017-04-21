import org.factcast.client.grpc.GrpcFactStoreAdapter;
import org.factcast.core.DefaultFactFactory;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.server.grpc.api.RemoteFactCast;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class SmokeMT {
	public static void main(String[] args) {
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);

		DefaultFactFactory ff = new DefaultFactFactory(new ObjectMapper());

		RemoteFactCast adapter = new GrpcFactStoreAdapter();
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

		adapter.subscribeId(SubscriptionRequest.catchup(FactSpec.ns("default")).sinceInception(), f -> {
			System.err.println("csubId " + f);

		});

		adapter.subscribeFact(SubscriptionRequest.catchup(FactSpec.ns("default")).sinceInception(), f -> {
			System.err.println("csubFact " + f);
		});

	}
}
