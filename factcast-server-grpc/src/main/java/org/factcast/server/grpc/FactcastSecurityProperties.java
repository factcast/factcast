package org.factcast.server.grpc;

import lombok.Data;

@Data
public class FactcastSecurityProperties {
  /** Enables/disables security validation. */
  boolean enabled = true;
}
