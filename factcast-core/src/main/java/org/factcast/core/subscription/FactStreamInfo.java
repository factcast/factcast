package org.factcast.core.subscription;

import lombok.Value;

@Value
public class FactStreamInfo {
  long startSerial;
  long horizonSerial;

  public int calculatePercentage(long currentSerial) {
    long num = horizonSerial - startSerial;
    long pos = currentSerial - startSerial;

    return (int) ((100.0 / num) * pos);
  }
}
