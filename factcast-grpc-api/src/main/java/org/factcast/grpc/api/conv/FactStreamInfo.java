package org.factcast.grpc.api.conv;

import lombok.Value;

@Value
public class FactStreamInfo {
  long startSerial;
  long horizonSerial;
}
