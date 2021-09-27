package org.factcast.itests.docexample.factcastlowlevel;

import java.util.Set;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CustomerEmailsProjectionTest {

    CustomerEmailsProjection uut; // unit under test

    @BeforeEach
    void setup() {
        uut = new CustomerEmailsProjection();
    }

    @Test
    void emailIsAddedWhenCustomerAdded() {
        // arrange
        String jsonPayload = String.format(
                "{\"id\":\"%s\", \"email\": \"%s\"}",
                UUID.randomUUID(),
                "customer@bar.com");
        Fact customerAdded = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(jsonPayload);

        // act
        uut.handleCustomerAdded(customerAdded);

        // assert
        Set<String> emails = uut.getCustomerEmails();
        assertThat(emails).hasSize(1).containsExactly("customer@bar.com");
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

        uut.handleCustomerAdded(customerAdded);
        uut.handleCustomerEmailChanged(customerEmailChanged);
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

        uut.handleCustomerAdded(customerAdded);
        uut.handleCustomerRemoved(customerRemoved);
        var emails = uut.getCustomerEmails();

        assertThat(emails).isEmpty();
    }

    @Test
    void applyingFactsWorks() {
        var customerId1 = UUID.randomUUID();
        var customerId2 = UUID.randomUUID();

        Fact customer1Added = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId1,
                        "customer1@bar.com"));

        Fact customer1EmailChanged = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerEmailChanged")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId1,
                        "customer1-new@bar.com"));

        Fact customer2Added = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId2,
                        "customer2@bar.com"));

        Fact customer2Removed = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerRemoved")
                .version(1)
                .build(String.format("{\"id\":\"%s\"}", customerId2));

        uut.apply(customer1Added);
        uut.apply(customer1EmailChanged);
        uut.apply(customer2Added);
        uut.apply(customer2Removed);

        var emails = uut.getCustomerEmails();
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("customer1-new@bar.com");
    }

}
