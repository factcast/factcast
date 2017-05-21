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
    /**
     * defines the number of Facts being retrieved with one Page Query
     */
    private int factPageSize = 500;

    /**
     * defines the number of Facts being retrieved with one Page Query, when
     * fetching only IDs
     */
    private int idPageSize = 10000;

}
