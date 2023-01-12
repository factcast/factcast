package org.factcast.store.internal.filter.blacklist;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FilesystemBlacklistFetcher implements BlacklistFetcher {
  @Override
  public Map<UUID, String> fetchBlacklist(){
    File file = new File(base, "blacklist.yaml");

  }

  private static Optional<Map<UUID, String>> parse_blacklist(){

  }
}
