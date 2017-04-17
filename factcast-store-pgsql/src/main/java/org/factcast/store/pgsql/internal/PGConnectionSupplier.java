package org.factcast.store.pgsql.internal;

import com.google.common.base.Supplier;
import com.impossibl.postgres.api.jdbc.PGConnection;

public interface PGConnectionSupplier extends Supplier<PGConnection> {

}
