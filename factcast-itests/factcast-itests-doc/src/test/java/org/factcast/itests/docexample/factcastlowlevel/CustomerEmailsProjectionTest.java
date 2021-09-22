package org.factcast.itests.docexample.factcastlowlevel;

import org.factcast.core.Fact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class CustomerEmailsProjectionTest {

    CustomerEmailsProjection uut; // unit under test

    @BeforeEach
    void setup() {
        uut = new CustomerEmailsProjection();
    }

    @Test
    void emailIsAdded() {
        Fact customerAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        UUID.randomUUID(),
                        "customer@bar.com"));

        uut.dispatchFacts(customerAdded);
        var emails = uut.getCustomerEmails();

        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("customer@bar.com");
    }

    @Test
    void emailIsChanged() {
        var customerId = UUID.randomUUID();

        Fact customerAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId,
                        "customer@bar.com"));

        Fact customerEmailChanged = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerEmailChanged")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId,
                        "customer-new@bar.com"));

        uut.dispatchFacts(customerAdded);
        uut.dispatchFacts(customerEmailChanged);
        var emails = uut.getCustomerEmails();

        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("customer-new@bar.com");
    }

    @Test
    void emailIsRemoved() {
        var customerId = UUID.randomUUID();

        Fact customerAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId,
                        "customer@bar.com"));

        Fact customerRemoved = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerRemoved")
                .version(1)
                .build(String.format("{\"id\":\"%s\"}", customerId));

        uut.dispatchFacts(customerAdded);
        uut.dispatchFacts(customerRemoved);
        var emails = uut.getCustomerEmails();

        assertThat(emails).isEmpty();
    }
}
