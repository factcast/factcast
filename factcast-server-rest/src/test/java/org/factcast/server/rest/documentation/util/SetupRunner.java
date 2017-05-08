package org.factcast.server.rest.documentation.util;

import java.util.Arrays;

import org.factcast.core.store.FactStore;
import org.factcast.server.rest.TestFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.NonNull;

@Component
public class SetupRunner {

    @Autowired
    public SetupRunner(@NonNull FactStore factStore) {
        factStore.publish(Arrays.asList(TestFacts.one));
    }

}
