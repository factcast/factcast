package org.factcast.example.smilepoc.bench;

import java.util.UUID;

public record KeySample(String cacheKey, UUID factId, int version, String chainId) {}
