---
title: "Hitchhiker's Guide To Testing"
weight: 10 
type: docs
---



{{% alert title="Preface" %}} 

This guide refers to a *fact* as the JSON based data structure which
is handled by the low-level FactCast API (see class `Fact`). In contrast, an *event* is an abstraction of the Factus
library which hides these details and uses Java POJOs instead (see `EventObject`).

The examples below may use Spring Boot or Lombok. None
of those frameworks are necessary for testing, as the Junit5 extension is framework-agnostic. {{% / alert %}}

## Introduction

An event-sourced application usually performs [two kinds of interactions]({{< ref "/concept">}}) with the FactCast
server:

- It subscribes to facts and builds up use-case specific views of the received data. These use-case specific views are
  called *projections*.
- It publishes new facts to the event log.

Building up *projections* works on both APIs, low-level and Factus. However, to simplify development, the
high-level Factus API has [explicit support for this concept]({{< ref "/usage/factus/projections/types">}}).


### Unit Tests

*Projections* are best tested in isolation, ideally at the unit test level. In the end, they are classes receiving facts
and updating some internal state. However, as soon as the projection's state is externalized (e.g. 
[see here]({{< ref "/usage/factus/projections/atomicity/" >}})) this test approach can get challenging.

### Integration Tests

Integration tests check the interaction of more than one component. Here, we're looking at integration tests that
validate the correct behaviour of a projection that uses an external data store like a Postgres database.

Be aware that FactCast integration tests as shown below can startup real infrastructure via Docker. For this reason,
they **usually perform significantly slower** than unit tests.

----

## Testing FactCast (low-level)

This section introduces the `CustomerEmails` projection for which we will write

- unit tests and
- integration tests.

For interaction with FactCast we are using the low-level API.

### The Customer Emails Projection

Imagine our application needs a set of customer emails currently in use in the System. To provide this information, we
identified these facts which contain the relevant data:

- `CustomerAdded`
- `CustomerEmailChanged`
- `CustomerRemoved`

`CustomerAdded` and `CustomerEmailChanged` both contain a customer ID and the email address. The `CustomerRemoved` fact
only carries the customer ID to remove.

Here is a possible projection using the FactCast low-level API:

```java
@Slf4j
public class CustomerEmailsProjection {

  private final Map<UUID, String> customerEmails = new HashMap<>();

  @NonNull
  public Set<String> getCustomerEmails() {
    return new HashSet<>(customerEmails.values());
  }

  public void apply(Fact fact) {
    switch (fact.type()) {
      case "CustomerAdded":
        handleCustomerAdded(fact);
        break;
      case "CustomerEmailChanged":
        handleCustomerEmailChanged(fact);
        break;
      case "CustomerRemoved":
        handleCustomerRemoved(fact);
        break;
      default:
        log.error("Fact type {} not supported", fact.type());
        break;
    }
  }

  @VisibleForTesting
  void handleCustomerAdded(Fact fact) {
    JsonNode payload = parsePayload(fact);
    customerEmails.put(extractIdFrom(payload), extractEmailFrom(payload));
  }

  @VisibleForTesting
  void handleCustomerEmailChanged(Fact fact) {
    JsonNode payload = parsePayload(fact);
    customerEmails.put(extractIdFrom(payload), extractEmailFrom(payload));
  }

  @VisibleForTesting
  void handleCustomerRemoved(Fact fact) {
    JsonNode payload = parsePayload(fact);
    customerEmails.remove(extractIdFrom(payload));
  }

  // helper methods:

  @SneakyThrows
  private JsonNode parsePayload(Fact fact) {
    return FactCastJson.readTree(fact.jsonPayload());
  }

  private UUID extractIdFrom(JsonNode payload) {
    return UUID.fromString(payload.get("id").asText());
  }
  private String extractEmailFrom(JsonNode payload) {
    return payload.get("email").asText();
  }
}
```

The method `apply` acts as an entry point for the projection and dispatches the received `Fact` to the appropriate
handling behavior. There, the `Fact` object's JSON payload is parsed using the Jackson library and the projection's data
(the `customerEmails` map), is updated accordingly.

Note, that we chose to avoid using a raw `ObjectMapper` here, but instead use the helper class `FactCastJson` as it contains a
pre-configured ObjectMapper. 

To query the projection for the customer emails, the `getCustomerEmails()` method returns the values of our
internal `customerEmails` map's values copied to a new `Set`.

### Unit Tests

Unit Testing this projection is very easier, as there are no external dependencies. We use `Fact` objects as input and
check the customized view of the internal map.

Let's look at an example for the `CustomerAdded` fact:

```java
    @Test
    void emailIsAddedWhenCustomerAdded(){
            // arrange
            String jsonPayload=String.format(
            "{\"id\":\"%s\", \"email\": \"%s\"}",
            UUID.randomUUID(),
            "customer@bar.com");
            Fact customerAdded=Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("CustomerAdded")
            .version(1)
            .build(jsonPayload);

            // act
            uut.handleCustomerAdded(customerAdded);

            // assert
            Set<String> emails=uut.getCustomerEmails();
            assertThat(emails).hasSize(1).containsExactly("customer@bar.com");
        }
```

Note the use of the convenient builder the `Fact` class is providing.

Since the focus of this unit test is on `handleCustomerAdded`, we execute the method directly.
[The full unit test](https://github.com/factcast/factcast/tree/master/factcast-itests/factcast-itests-doc/src/test/java/org/factcast/itests/docexample/factcastlowlevel/CustomerEmailsProjectionTest.java)
also contains a test for the dispatching logic of the `apply` method, as well as similar tests for the other handlers.

Checking your projection's logic should preferably be done with unit tests in the first place, even though you might
also want to add an integration test to prove it to work in conjunction with its collaborators.

### Integration Tests

FactCast [provides a Junit5 extension]({{< ref "/usage/factus/testing.md" >}}) which starts a FactCast server
pre-configured for testing plus its Postgres database via the excellent testcontainers library and resets their state
between test executions.

#### Preparation

Before writing your first integration test

- make sure Docker is installed and running on your machine
- add the `factcast-test` module to your `pom.xml`:

```xml
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-test</artifactId>
  <version>${factcast.version}</version>
  <scope>test</scope>
</dependency>
```


// TODO: check it should be UPGRADE or if integrationtest mode should already do this  

- to allow TLS free authentication between our test code and the local FactCast server, create
  an `application.properties` file in the project's `resources` directory with the following content:

```properties
grpc.client.factstore.negotiationType=PLAINTEXT
```

This will make the client application connect to the server without using TLS.

#### Writing The Integration Test

Our integration test builds upon the previous unit test example. This time however, we want to check if the
`CustomerEmailsProjection` can also be updated by a real FactCast server:

```java
@SpringBootTest
@ExtendWith(FactCastExtension.class)
public class CustomerEmailsProjectionITest {

  @Autowired FactCast factCast;
  CustomerEmailsProjection uut= new CustomerEmailsProjection();

  private class FactObserverImpl implements FactObserver {
    @Override
    public void onNext(@NonNull Fact fact) {
      uut.apply(fact);
    }
  }

  @Test
  void emailOfSingleCustomer() {
    // arrange
    UUID customerId1 = UUID.randomUUID();
    Fact customer1Added =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("CustomerAdded")
            .version(1)
            .build(
                String.format(
                    "{\"id\":\"%s\", \"email\": \"%s\"}", customerId1, "customer1@bar.com"));

    factCast.publish(customer1Added);

    // act
    var subscriptionRequest =
        SubscriptionRequest.catchup(FactSpec.ns("user").type("CustomerAdded"))
            .or(FactSpec.ns("user").type("CustomerEmailChanged"))
            .or(FactSpec.ns("user").type("CustomerRemoved"))
            .fromScratch();

    factCast.subscribe(subscriptionRequest, new FactObserverImpl()).awaitComplete();

    // assert
    var customerEmails = uut.getCustomerEmails();
    assertThat(customerEmails).hasSize(1);
    assertThat(customerEmails).containsExactly("customer1@bar.com");
  }
  //...
}
```

The previously mentioned `FactCastExtension` starts the FactCast server and the Postgres database *once* before the
first test is executed. Between the tests, the extension wipes all old facts from the FactCast server so that you are
guaranteed to always start from scratch.

Once a fact is received, FactCast invokes the `onNext` method of the `FactObserverImpl`, which delegates to the `apply`
method of the `CustomerEmailsProjection`.

For details of the FactCast low-level API please refer to [the API documentation]({{< ref "/usage/lowlevel/java">}}).

## Testing with Factus

Factus builds up on the low-level FactCast API and provides a higher level of abstraction. To see Factus in action we
use a scenario which is very similar to what you have seen before. This time we have a `UserEmailsProjection` which we
will ask for a unique list of user emails.

These are the events we need to handle:

- [`UserAdded`](https://github.com/factcast/factcast/tree/master/factcast-itests/factcast-itests-doc/src/main/java/org/factcast/itests/docexample/factus/event/UserAdded.java)
  ,
- [`UserEmailChanged`](https://github.com/factcast/factcast/tree/master/factcast-itests/factcast-itests-doc/src/main/java/org/factcast/itests/docexample/factus/event/UserEmailChanged.java)
  and
- [`UserRemoved`](https://github.com/factcast/factcast/tree/master/factcast-itests/factcast-itests-doc/src/main/java/org/factcast/itests/docexample/factus/event/UserRemoved.java)
  .

The `UserAdded` and `UserEmailChanged` event contain two properties, the user ID and the email. The `UserRemoved` event
only contains the user ID.

### An Example Event

To get an idea of how the events are defined, let's have a look inside `UserAdded`:

```java
@Data // provides getter + setter, hashCode, equals, toString (Lombok)
@Specification(ns = "user", type = "UserAdded", version = 1)
public class UserAdded implements EventObject {

  private final UUID userId;
  private final String email;

  // hint Jackson deserializer
  @ConstructorProperties({"userId", "email"})
  public UserAdded(UUID userId, String email) {
    this.userId = userId;
    this.email = email;
  }

  @Override
  public Set<UUID> aggregateIds() {
    return Collections.emptySet();
  }
}
```

We create a Factus compatible event by implementing the `EventObject` interface and supplying the fact details via
the `@Specification` annotation. The event itself contains the properties `userId` and `email` which are simply fields
of the `UserAdded` class. The `@ConstructorProperties` annotation helps the Jackson deserializer to identify the right
constructor arguments. For more details on how to define a Factus event read on 
[here]({{< ref "/usage/factus/introduction">}}).

### The User Emails Projection

Now that we know which events to handle, we can process them in the `UserEmailsProjection`:

```java
public class UserEmailsProjection extends LocalManagedProjection {

  private final Map<UUID, String> userEmails = new HashMap<>();

  public Set<String> getEmails() {
    return new HashSet<>(userEmails.values());
  }

  @Handler
  void apply(UserAdded event) {
    userEmails.put(event.getUserId(), event.getEmail());
  }

  @Handler
  void apply(UserEmailChanged event) {
// needs to assert user exists
    userEmails.put(event.getUserId(), event.getEmail());
  }

  @Handler
  void apply(UserRemoved event) {
    userEmails.remove(event.getUserId());
  }
}
```

You will instantly notice how short this implementation is compared to the `CustomerEmailsProjection` class before. No
dispatching or explicit JSON parsing is needed. Instead, the event handler methods each receive their event as plain
Java POJO which is ready to use.

As projection type we decided for a [`LocalManagedProjection`]({{< ref "local-managed-projection.md" >}})
which is intended for self-managed, in-memory use cases. See [here]({{< ref "/usage/factus/projections/types" >}}) for
detailed reading on the various Factus supported projection types.

### Unit Tests

The unit test for this projection tests each handler method individually. As an example, here is the test for
the `UserAdded` event handler:

```java
@Test
void emailIsAddedWhenUserAddedEvent(){
        // arrange
        UUID someUserId=UUID.randomUUID();
        UserAdded userAddedEvent=new UserAdded(someUserId,"foo@bar.com")

        // act
        UserEmailsProjection uut=new UserEmailsProjection();
        uut.apply(userAddedEvent);
        var emails=uut.getEmails();

        // assert
        assertThat(emails).hasSize(1);
        assertThat(emails).containsExactly("foo@bar.com");
        }
```

First we create a `userAddedEvent` which we then `apply` to the responsible handler method of the `UserEmailsProjection`
class. To check the result, we fetch the `Set` of emails and, as last part, examine the content.

#### Integration Test

After we have covered each handler method with detailed tests on unit level, we also want an integration test to test
against a real FactCast server.

Here is an example:

```java

@SpringBootTest
@ExtendWith(FactCastExtension.class)
public class UserEmailsProjectionITest {

  @Autowired
  Factus factus;
  UserEmailsProjection uut= new ...;

  @Test
  void emailOfSingleUser_TODO() {
    // arrange
    UUID someUserId = UUID.randomUUID();
    UserAdded userAddedEvent = new UserAdded(someUserId, "user1@bar.com");
    factus.publish(userAddedEvent);

    // act
    factus.update(uut);
    var emails = uut.getEmails();

    // assert
    assertThat(emails).hasSize(1);
    assertThat(emails).containsExactly("user1@bar.com");
  }
```

The annotations of the test class are identical to the integration test shown for the low-level API. Hence, we only
introduce them quickly here:

- `@SpringBootTest`
    - starts a Spring container to enable dependency injection of the `factus` Spring bean
- `@ExtendWith(FactCastExtension.class)`
    - starts a FactCast and its Postgres database in the background
    - erases old events inside FactCast before each test

The test itself first creates a `UserAdded` event which is then published to FactCast. Compared to the low-level
integration test, the "act" part is slim and shows the power of the Factus API:
The call to `factus.update(...)` builds a subscription request for all the handled events of the `UserEmailsProjection`
class. The events returned from FactCast are then automatically applied to the correct handler.

The test concludes by checking if the state of the `UserEmailsProjection` was updated as correctly.

## Full Example Code

The code for all examples introduced here can be
found [here](https://github.com/factcast/factcast/tree/master/factcast-itests/factcast-itests-doc/).
