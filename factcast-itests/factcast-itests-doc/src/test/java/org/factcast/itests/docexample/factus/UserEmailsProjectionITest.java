package org.factcast.itests.docexample.factus;

import org.factcast.factus.Factus;
import org.factcast.itests.docexample.factus.event.UserAdded;
import org.factcast.itests.docexample.factus.event.UserRemoved;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ExtendWith(FactCastExtension.class)
public class UserEmailsProjectionITest {

    @Autowired
    Factus factus;

    UserEmailsProjection uut = new UserEmailsProjection();

    @Test
    void projectionHandlesUserAddedEvent() {
        UserAdded userAdded = UserAdded.of(UUID.randomUUID(), "user@bar.com");
        factus.publish(userAdded);
        factus.update(uut);

        Set<String> emails = uut.getEmails();
        assertThat(emails).hasSize(1).containsExactly("user@bar.com");
    }

    @Test
    void projectionHandlesUserAddedAndUserRemovedEvents() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        factus.publish(List.of(
                UserAdded.of(userId1, "user1@bar.com"),
                UserAdded.of(userId2, "user2@bar.com"),
                UserRemoved.of(userId2)
        ));

        factus.update(uut);

        Set<String> emails = uut.getEmails();
        assertThat(emails).hasSize(1).containsExactly("user1@bar.com");
    }

}
