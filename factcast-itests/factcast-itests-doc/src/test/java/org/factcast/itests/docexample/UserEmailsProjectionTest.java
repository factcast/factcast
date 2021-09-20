package org.factcast.itests.docexample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserEmailsProjectionTest {

    UserEmailsProjection uut; // unit under test

    @BeforeEach
    void setup() {
        uut = new UserEmailsProjection();
    }

    @Test
    public void emailIsAdded() {
        System.out.println("buh");
//        uut.apply(new UserAdded().setEmail("foo@bar.com"));
//        var emails = uut.
//        getUserEmails();
//        assertThat(emails).hasSize(1);
//        assertThat(emails).containsExactly("foo@bar.com");
    }

    @Test
    public void emailIsChanged() {
/*
        uut.apply(new UserAdded().setEmail("foo@bar.com"));
        uut.apply(new UserEmailChanged().setEmail("something@else.com"));
        var emails = uut.getUserEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("something@else.com");
*/
    }

    @Test
    public void emailRemoved() {
//        uut.apply(new UserAdded().setEmail("foo@bar.com"));
//        uut.apply(new UserRemoved().setEmail("foo@bar.com"));
//        var emails = uut.getUserEmails();
//        assertThat(emails).isEmpty();
    }
}