package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.subscription.FactStoreObserver;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A PostGres based FactStore implementation
 * 
 * @author usr
 *
 */
@RequiredArgsConstructor
class PGFactStore implements FactStore {
	// is that interesting to configure?
	private static final int BATCH_SIZE = 500;
	@NonNull
	private final JdbcTemplate tpl;
	@NonNull
	private final PGFactFactory factory;
	@NonNull
	private final PGSubscriptionFactory sf;

	@Override
	@Transactional
	public void publish(@NonNull Iterable<Fact> factsToPublish) {

		List<Fact> l = Lists.newArrayList(factsToPublish);

		tpl.batchUpdate(PGConstants.INSERT_FACT, l, BATCH_SIZE, (p, f) -> {
			p.setString(1, f.jsonHeader());
			p.setString(2, f.jsonPayload());
		});
	}

	private Fact extractFactFromResultSet(ResultSet rs, int rowNum) throws SQLException {
		return factory.extractData(rs);
	}

	@Override
	public CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req,
			@NonNull FactStoreObserver observer) {
		return CompletableFuture.supplyAsync(() -> sf.subscribe(req, observer));
	}

	@Override
	public Optional<Fact> fetchById(@NonNull UUID id) {
		return tpl.query(PGConstants.SELECT_BY_ID, new Object[] { "{\"id\":\"" + id + "\"}" },
				this::extractFactFromResultSet).stream().findFirst();
	}

}
