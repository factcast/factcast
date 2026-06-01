package org.factcast.example.smilepoc.bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.loader.CacheKeyParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fetches a randomized sample of cache keys, shuffled with a fixed seed for reproducibility. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeySampler {

  private static final long SEED = 42L;

  private final JdbcTemplate jdbc;

  private List<KeySample> cached;

  public synchronized List<KeySample> sample(int target) {
    if (cached != null && cached.size() >= target) {
      return cached.subList(0, target);
    }
    log.info("Sampling up to {} keys from transformationcache_jsonb", target);
    List<String> rawKeys =
        jdbc.queryForList(
            "SELECT cache_key FROM transformationcache_jsonb "
                + "TABLESAMPLE BERNOULLI (LEAST(100, ? * 100.0 / GREATEST(1, "
                + "(SELECT count(*) FROM transformationcache_jsonb)))) LIMIT ?",
            String.class,
            target,
            target);

    if (rawKeys.size() < target) {
      log.info(
          "Sample returned {} (asked for {}); falling back to ORDER BY random() LIMIT {}",
          rawKeys.size(),
          target,
          target);
      rawKeys =
          jdbc.queryForList(
              "SELECT cache_key FROM transformationcache_jsonb ORDER BY random() LIMIT ?",
              String.class,
              target);
    }

    List<KeySample> result = new ArrayList<>(rawKeys.size());
    for (String k : rawKeys) {
      try {
        CacheKeyParser.Parsed p = CacheKeyParser.parse(k);
        result.add(new KeySample(k, p.factId(), p.version(), p.chainId()));
      } catch (Exception ignore) {
        // skip malformed
      }
    }
    Collections.shuffle(result, new Random(SEED));
    cached = result;
    log.info("Sampled {} usable keys", cached.size());
    return cached;
  }

  public KeySample anyKnown() {
    String key =
        jdbc.queryForObject(
            "SELECT cache_key FROM transformationcache_jsonb LIMIT 1", String.class);
    if (key == null) throw new IllegalStateException("transformationcache_jsonb is empty");
    CacheKeyParser.Parsed p = CacheKeyParser.parse(key);
    return new KeySample(key, p.factId(), p.version(), p.chainId());
  }

  public UUID anyFactId() {
    return anyKnown().factId();
  }
}
