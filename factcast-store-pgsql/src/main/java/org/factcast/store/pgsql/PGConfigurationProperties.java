package org.factcast.store.pgsql;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.experimental.Accessors;

@Component
@ConfigurationProperties(prefix = "factcast.pg")
@Data
@Accessors(fluent = false)
public class PGConfigurationProperties {
    private int fetchSize = 10;// FIXME

    private long notificationWaitTimeInMillis = 1000 * 120;
}
