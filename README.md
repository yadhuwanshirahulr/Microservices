# Microservices

## Recent Changes

The latest changes improve runtime configuration refresh support across the
`accounts`, `cards`, and `loans` microservices.

### 1. Contact Info DTOs Updated for Runtime Refresh

The contact information configuration DTOs were changed from Java `record`
types to normal mutable classes.

Updated files:

- `accounts/src/main/java/com/eazybytes/accounts/dto/AccountsContatctInfoDto.java`
- `cards/src/main/java/com/eazybytes/cards/dto/CardsContactInfoDto.java`
- `loans/src/main/java/com/eazybytes/loans/dto/LoansContactInfo.java`

Previous approach:

```java
@ConfigurationProperties(prefix = "accounts")
public record AccountsContatctInfoDto(
    String message,
    Map<String, String> contactDetails,
    List<String> onCallSupport
) {
}
```

Updated approach:

```java
@ConfigurationProperties(prefix = "accounts")
@Getter
@Setter
public class AccountsContatctInfoDto {
    private String message;
    private Map<String, String> contactDetails;
    private List<String> onCallSupport;
}
```

### Why This Change Was Made

Java records are immutable by design. They are useful when values only need to
be read, but they are not suitable when configuration properties need to be
refreshed and rebound at runtime.

By changing the DTOs to mutable classes with Lombok `@Getter` and `@Setter`,
Spring can update the configuration property values after a refresh event
without requiring the microservice to be restarted.

### 2. Actuator Endpoints Enabled

Spring Boot Actuator web endpoints were enabled in the `application.yml` files
for the `accounts`, `cards`, and `loans` services.

Updated files:

- `accounts/src/main/resources/application.yml`
- `cards/src/main/resources/application.yml`
- `loans/src/main/resources/application.yml`

Added configuration:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

### Why This Change Was Made

Exposing actuator endpoints allows each microservice to receive refresh
requests. This is useful when configuration values are changed in Config Server
and the running service needs to reload the latest values without a restart.

### Runtime Refresh Flow

1. Update configuration values in Config Server.
2. Trigger the actuator refresh endpoint for the required microservice.
3. Spring reloads the updated configuration properties.
4. The updated mutable DTO receives the latest values at runtime.

### Notes

- This change affects the configuration binding behavior only.
- No business logic changes were made in the service layer.
- The same refresh pattern is now available in the accounts, cards, and loans
  microservices.

## New Uncommitted Change Notes

The current unsaved and uncommitted workspace changes add Spring Cloud Bus with
RabbitMQ support across the `accounts`, `cards`, and `loans` microservices.

### 1. Spring Cloud Bus AMQP Dependency Added

The following service `pom.xml` files now include the
`spring-cloud-starter-bus-amqp` dependency:

- `accounts/pom.xml`
- `cards/pom.xml`
- `loans/pom.xml`

Added dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bus-amqp</artifactId>
    <version>4.3.2</version>
    <scope>compile</scope>
</dependency>
```

### Why This Change Was Made

Spring Cloud Bus allows configuration refresh events to be broadcast through a
message broker. With the AMQP starter, the services can use RabbitMQ to receive
refresh events instead of refreshing each service manually one by one.

### 2. RabbitMQ Connection Configuration Added

RabbitMQ connection settings were added to each service `application.yml` file:

- `accounts/src/main/resources/application.yml`
- `cards/src/main/resources/application.yml`
- `loans/src/main/resources/application.yml`

Added configuration:

```yaml
spring:
  rabbitmq:
    host: "localhost"
    port: 5672
    username: "guest"
    password: "guest"
```

### Why This Change Was Made

These settings point each microservice to a local RabbitMQ broker. RabbitMQ is
used as the message transport for Spring Cloud Bus refresh events.

### 3. IntelliJ VCS Mapping Updated

The `.idea/vcs.xml` file now includes a Git mapping for:

- `configServer/target/config-repo`

This lets IntelliJ treat the generated config repository directory as a Git
repository inside the project workspace.

### Updated Runtime Refresh Flow With Spring Cloud Bus

1. Update configuration values in the Config Server repository.
2. Commit or make the config changes available to Config Server.
3. Trigger the bus refresh endpoint on one running microservice.
4. Spring Cloud Bus publishes the refresh event through RabbitMQ.
5. Other connected microservices receive the event and reload their
   configuration without restarting.
