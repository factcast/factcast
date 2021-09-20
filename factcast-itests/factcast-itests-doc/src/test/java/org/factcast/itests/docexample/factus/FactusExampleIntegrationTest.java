package org.factcast.itests.docexample.factus;

import org.factcast.factus.Factus;
import org.factcast.itests.docexample.UserEmailsProjection;
import org.factcast.itests.docexample.event.UserAdded;
import org.factcast.itests.docexample.event.UserEmailChanged;
import org.factcast.itests.docexample.event.UserRemoved;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
// provide fresh application context including uut for each test
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ExtendWith({FactCastExtension.class})
public class FactusExampleIntegrationTest {

    @Autowired
    Factus factus;

    @Autowired
    UserEmailsProjection uut;

    @Test
    public void emailOfSingleUser() {
        factus.publish(new UserAdded(UUID.randomUUID(), "user1@bar.com"));
        factus.update(uut);
        var emails = uut.getEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("user1@bar.com");
    }

    @Test
    public void emailOfMultipleUsers() {
        var userId1 = UUID.randomUUID();
        var userId2 = UUID.randomUUID();
        factus.publish(List.of(
                new UserAdded(userId1, "user1@bar.com"),
                new UserAdded(userId2, "user2@bar.com"),
                new UserEmailChanged(userId1, "user1-new@bar.com"),
                new UserRemoved(userId2)
        ));

        factus.update(uut);

        var emails = uut.getEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("user1-new@bar.com");
    }

}
