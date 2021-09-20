package org.factcast.itests.docexample;

import org.factcast.itests.docexample.event.UserAdded;
import org.factcast.itests.docexample.event.UserEmailChanged;
import org.factcast.itests.docexample.event.UserRemoved;
import org.junit.Before;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserEmailProjectionTest {

    UserEmailProjection uut; // unit under test

    @Before
    void setup() {
        uut = new UserEmailProjection();
    }

    @Test
    public void emailIsAdded() {
        uut.apply(new UserAdded().setEmail("foo@bar.com"));
        var emails = uut.getUserEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("foo@bar.com");
    }

    @Test
    public void emailIsChanged() {
        uut.apply(new UserAdded().setEmail("foo@bar.com"));
        uut.apply(new UserEmailChanged().setEmail("something@else.com"));
        var emails = uut.getUserEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("something@else.com");
    }

    @Test
    public void emailRemoved() {
        uut.apply(new UserAdded().setEmail("foo@bar.com"));
        uut.apply(new UserRemoved().setEmail("foo@bar.com"));
        var emails = uut.getUserEmails();
        assertThat(emails).isEmpty();
    }
}