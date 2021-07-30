package org.factcast.core.subscription;

import lombok.Value;

@Value
public class FactStreamInfo {
  long startSerial;
  long horizonSerial;
}
