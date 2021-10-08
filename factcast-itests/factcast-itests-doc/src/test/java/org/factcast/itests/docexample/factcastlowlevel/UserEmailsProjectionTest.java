package org.factcast.itests.docexample.factcastlowlevel;

import java.util.Set;

import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserEmailsProjectionTest {

    UserEmailsProjection uut = new UserEmailsProjection();

    @Test
    void whenHandlingUserAddedFactEmailIsAdded() {
        // arrange
        String jsonPayload = String.format(
                "{\"id\":\"%s\", \"email\": \"%s\"}",
                UUID.randomUUID(),
                "user@bar.com");
        Fact userAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("UserAdded")
                .version(1)
                .build(jsonPayload);

        // act
        uut.handleUserAdded(userAdded);

        // assert
        Set<String> emails = uut.getUserEmails();
        assertThat(emails).hasSize(1).containsExactly("user@bar.com");
    }

    @Test
    void whenHandlingUserRemovedFactEmailIsRemoved() {
        UUID userId = UUID.randomUUID();

        Fact userAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("UserAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        userId,
                        "user@bar.com"));

        Fact userRemoved = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("UserRemoved")
                .version(1)
                .build(String.format("{\"id\":\"%s\"}", userId));

        uut.handleUserAdded(userAdded);
        uut.handleUserRemoved(userRemoved);
        Set<String> emails = uut.getUserEmails();

        assertThat(emails).isEmpty();
    }


    @Test
    void userAddedFactIsApplied() {
        UserEmailsProjection projection = spy(new UserEmailsProjection());
        String jsonPayload = String.format(
                "{\"id\":\"%s\", \"email\": \"%s\"}",
                UUID.randomUUID(),
                "user@bar.com");
        Fact userAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("UserAdded")
                .version(1)
                .build(jsonPayload);

        projection.apply(userAdded);

        verify(projection).handleUserAdded(any(Fact.class));
    }

    @Test
    void userDeletedFactIsApplied() {
        UserEmailsProjection projection = spy(new UserEmailsProjection());
        Fact userRemoved = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("UserRemoved")
                .version(1)
                .build(String.format("{\"id\":\"%s\"}", UUID.randomUUID()));

        projection.apply(userRemoved);

        verify(projection).handleUserRemoved(any(Fact.class));
    }
}
