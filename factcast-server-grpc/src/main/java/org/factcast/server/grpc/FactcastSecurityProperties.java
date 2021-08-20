package org.factcast.server.grpc;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = false)
public class FactcastSecurityProperties {
  /** Enables/disables security validation. */
  boolean enabled = true;
}
