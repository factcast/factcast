package org.factcast.itests.docexample.factus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.itests.docexample.UserEmailsProjection;
import org.factcast.itests.docexample.event.UserAdded;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

//@SpringBootTest
//@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
////@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
//@Testcontainers(disabledWithoutDocker = true)
//@ExtendWith({FactCastExtension.class})
@Slf4j
public class FactusExampleIntegrationTest {

    @Autowired
    Factus factus;

//    @Autowired
//    UserEmailsProjection uut;

    @Disabled
    @Test
    public void renameMe() {
//        System.out.println("foo");
//        factus.publish(new UserAdded().setEmail("foo@bar.com"));
//        factus.update(uut);
//        var emails = uut.getUserEmails();
//        assertThat(emails).hasSize(2);
//        assertThat(emails).containsExactly("foo@bar.com");
    }

    @Test
    public void renameMe2() {

    }

    @Data
    abstract private static class MinimalBaseEvent implements EventObject {
        private String someProperty;

        @Override
        public Set<UUID> aggregateIds() {
            return Collections.emptySet();
        }
    }
}
