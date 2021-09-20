package org.factcast.itests.docexample;

import org.factcast.itests.docexample.event.UserAdded;
import org.factcast.itests.docexample.event.UserEmailChanged;
import org.factcast.itests.docexample.event.UserRemoved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserEmailsProjectionTest {

    UserEmailsProjection uut; // unit under test
    UUID someUserId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        uut = new UserEmailsProjection();
    }

    @Test
    public void emailIsAdded() {
        uut.apply(new UserAdded(someUserId, "foo@bar.com"));
        var emails = uut.getEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("foo@bar.com");
    }

    @Test
    public void emailIsChanged() {
        uut.apply(new UserAdded(someUserId, "foo@bar.com"));
        uut.apply(new UserEmailChanged(someUserId, "something@else.com"));
        var emails = uut.getEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("something@else.com");
    }

    @Test
    public void emailRemoved() {
        uut.apply(new UserAdded(someUserId, "foo@bar.com"));
        uut.apply(new UserRemoved(someUserId));
        var emails = uut.getEmails();
        assertThat(emails).isEmpty();
    }
}