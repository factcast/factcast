package org.factcast.store.internal.filter.blacklist;

import java.util.Map;
import java.util.UUID;

public abstract class BlacklistFetcher {

  public Map<UUID, String> fetchBlacklist;

}
