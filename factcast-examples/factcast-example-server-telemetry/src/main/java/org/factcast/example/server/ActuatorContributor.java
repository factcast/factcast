package org.factcast.example.server;

import org.factcast.example.server.telemetry.MyTelemetryListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class ActuatorContributor implements InfoContributor {

  @Autowired
  MyTelemetryListener listener;

  public void contribute(Info.Builder builder) {
    builder.withDetail("followingSubscriptionsInfo", listener.getFollowingSubscriptionsInfo());
  }
}
