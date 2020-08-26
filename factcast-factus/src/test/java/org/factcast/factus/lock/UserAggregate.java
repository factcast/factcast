package org.factcast.factus.lock;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserAggregate extends Aggregate {

    private String name;

    @Handler
    void handle(UserCreated evt) {
        AggregateUtil.aggregateId(this, evt.aggId());
        this.name = evt.name();
    }
}
