package org.factcast.itests.docexample.factcastlowlevel;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ExtendWith(FactCastExtension.class)
public class CustomerRepositoryITest {

    @Autowired
    FactCast factCast;

    @Autowired
    CustomerRepository uut;

    @Test
    void emailOfSingleCustomer() {
        UUID customerId1 = UUID.randomUUID();
        Fact customer1Added = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId1,
                        "customer1@bar.com"));

        factCast.publish(customer1Added);

        var customerEmails = uut.getCustomerEmails();
        assertThat(customerEmails).hasSize(1);
        assertThat(customerEmails).containsExactly("customer1@bar.com");
    }

    @Test
    void emailOfMultipleCustomers() {
        UUID customerId1 = UUID.randomUUID();
        UUID customerId2 = UUID.randomUUID();

        Fact customer1Added = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId1,
                        "customer1@bar.com"));

        Fact customer2Added = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerAdded")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId2,
                        "customer2@bar.com"));

        Fact customer1EmailChanged = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerEmailChanged")
                .version(1)
                .build(String.format(
                        "{\"id\":\"%s\", \"email\": \"%s\"}",
                        customerId1,
                        "customer1-new@bar.com"));

        Fact customer2Removed = Fact.builder()
                .id(UUID.randomUUID())
                .ns("user")
                .type("CustomerRemoved")
                .version(1)
                .build(String.format("{\"id\":\"%s\"}", customerId2));

        factCast.publish(List.of(
                customer1Added,
                customer2Added,
                customer1EmailChanged,
                customer2Removed));

        var customerEmails = uut.getCustomerEmails();
        assertThat(customerEmails).hasSize(1);
        assertThat(customerEmails).containsExactly("customer1-new@bar.com");
    }
}
