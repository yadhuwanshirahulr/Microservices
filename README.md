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

## Docker Compose, Config Server, and Runtime Configuration Notes

This section documents the current Docker Compose and Spring Cloud Config setup
used by the project.

### What We Are Using

The project uses the following runtime components:

- `configserver`: Spring Cloud Config Server. It serves external configuration
  for `accounts`, `loans`, and `cards`.
- `rabbit`: RabbitMQ. It is used by Spring Cloud Bus to broadcast configuration
  refresh events between services.
- `accounts-ms`: Accounts microservice, exposed on host port `8080`.
- `loans-ms`: Loans microservice, exposed on host port `8090`.
- `cards-ms`: Cards microservice, exposed on host port `9000`.
- Docker Compose. It starts all service images on one Docker network so
  containers can talk to each other by service name.

### Why We Are Using This Setup

Externalizing configuration keeps environment-specific values outside the
service code. The same service image can run in different profiles such as
`default`, `qa`, or `prod` by reading the matching configuration from Config
Server.

RabbitMQ is used because Spring Cloud Bus needs a message broker. When a config
change is refreshed, one refresh event can be published to RabbitMQ and all
connected services can reload their latest properties.

Docker Compose is used to start the complete local environment with one command:

```powershell
docker compose up -d
```

### Config Server Code

The Config Server application is enabled by `@EnableConfigServer`:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }

}
```

This code is in:

```text
configServer/src/main/java/com/yeadhuwanshirahul/configServer/ConfigServerApplication.java
```

The Config Server runs on port `8071`:

```yaml
server:
  port: 8071
```

It reads configuration from the Git repository configured in
`configServer/src/main/resources/application.yml`:

```yaml
spring:
  profiles:
    active: git
  cloud:
    config:
      server:
        git:
          uri: "https://github.com/yadhuwanshirahulr/Configurations.git"
          default-label: main
          clone-on-start: true
          force-pull: true
```

Important fields:

- `active: git` tells Config Server to read config from Git.
- `uri` is the Git repository that contains files like `accounts-prod.yml`,
  `loans-prod.yml`, and `cards-prod.yml`.
- `clone-on-start: true` makes Config Server clone the repo when it starts.
- `force-pull: true` helps Config Server pull the latest remote config.

### Microservice Config Client Code

Each business microservice imports configuration from Config Server in its
`application.yml`.

Example from `accounts/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: "accounts"
  profiles:
    active: "prod"
  config:
    import: "optional:configserver:http://localhost:8071/"
```

When running directly on the host machine, `localhost:8071` points to Config
Server on the host.

When running inside Docker Compose, this value is overridden by
`docker-compose/default/common-config.yml`:

```yaml
microservice-configserver-config:
  environment:
    SPRING_PROFILES_ACTIVE: default
    SPRING_CONFIG_IMPORT: configserver:http://configserver:8071/
```

This is important because inside Docker, `localhost` means the current
container. The accounts container cannot use `localhost:8071` to reach Config
Server. It must use the Docker Compose service name:

```text
http://configserver:8071/
```

### How Profile Config Files Are Selected

Spring Cloud Config uses this pattern:

```text
/{application-name}/{profile}
```

For accounts with the `default` profile, the service reads:

```text
http://configserver:8071/accounts/default
```

For accounts with the `prod` profile, it reads:

```text
http://configserver:8071/accounts/prod
```

Because the accounts service has:

```yaml
spring:
  application:
    name: "accounts"
```

Config Server looks for files such as:

```text
accounts.yml
accounts-default.yml
accounts-prod.yml
accounts-qa.yml
```

The same rule applies to `loans` and `cards`:

```text
loans.yml
loans-prod.yml
cards.yml
cards-prod.yml
```

Example config served for accounts:

```yaml
build:
  version: "live"

accounts:
  message: "Message from live/prod profile"
  contactDetails:
    name: "live server"
    email: "live@server.com"
  onCallSupport:
    - (911)3456778
    - (911)2346789
```

### How Accounts Reads Config Values

The Accounts controller reads `build.version` from Config Server:

```java
@Value("${build.version}")
private String buildVersion;

@GetMapping("build-info")
public ResponseEntity<String> getBuildVersion(){
    return new ResponseEntity<>(buildVersion,HttpStatus.OK);
}
```

This endpoint can be tested at:

```text
http://localhost:8080/api/build-info
```

The contact info properties are bound into a DTO:

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

The DTO is enabled in the application class:

```java
@EnableConfigurationProperties(value = AccountsContatctInfoDto.class)
```

The controller returns the bound config values:

```java
@Autowired
private AccountsContatctInfoDto accountsContatctInfoDto;

@GetMapping("contact-info")
public ResponseEntity<AccountsContatctInfoDto> getContactInfo(){
    return new ResponseEntity<>(accountsContatctInfoDto,HttpStatus.OK);
}
```

Test endpoint:

```text
http://localhost:8080/api/contact-info
```

The same pattern is used by the Loans and Cards services with their own
configuration prefixes:

```java
@ConfigurationProperties(prefix = "loans")
```

```java
@ConfigurationProperties(prefix = "cards")
```

### Docker Compose Startup Flow

The main Compose file is:

```text
docker-compose/default/docker-compose.yml
```

RabbitMQ starts first:

```yaml
rabbit:
  image: rabbitmq:3.12-management
  hostname: rabbitmq
  ports:
    - "5672:5672"
    - "15672:15672"
  healthcheck:
    test: rabbitmq-diagnostics -q check_port_connectivity
```

Why this is needed:

- Port `5672` is used by services for AMQP messaging.
- Port `15672` is the RabbitMQ management UI.
- The healthcheck makes Compose wait until RabbitMQ is actually ready.

Config Server starts after RabbitMQ is healthy:

```yaml
configserver:
  image: "yadhuwanshirahulr/configserver:v2"
  container_name: configServer-ms
  ports:
    - "8071:8071"
  depends_on:
    rabbit:
      condition: service_healthy
  healthcheck:
    test: "curl --fail --silent localhost:8071/actuator/health/readiness|grep UP || exit 1"
```

Why this is needed:

- Config Server also connects to RabbitMQ for Spring Cloud Bus.
- The readiness healthcheck tells Compose when Config Server is ready to serve
  configuration.

Accounts, Loans, and Cards start after Config Server is healthy:

```yaml
accounts:
  image: "yadhuwanshirahulr/accounts:v2"
  container_name: accounts-ms
  ports:
    - "8080:8080"
  depends_on:
    configserver:
      condition: service_healthy
  environment:
    SPRING_APPLICATION_NAME: "accounts"
```

This same pattern is used for Loans and Cards:

```yaml
loans:
  ports:
    - "8090:8090"
  environment:
    SPRING_APPLICATION_NAME: "loans"

cards:
  ports:
    - "9000:9000"
  environment:
    SPRING_APPLICATION_NAME: "cards"
```

### Shared Docker Compose Configuration

Common settings are stored in:

```text
docker-compose/default/common-config.yml
```

All services are placed on the same Docker network:

```yaml
network-deploy-service:
  networks:
    - yadhuwanshirahulr
```

This lets containers call each other by Compose service name:

```text
rabbit
configserver
accounts
loans
cards
```

Common microservice settings:

```yaml
microservice-base-config:
  environment:
    SPRING_RABBITMQ_HOST: "rabbit"
```

This overrides local RabbitMQ settings inside Docker. The services use
`rabbit:5672`, not `localhost:5672`.

Config Server client settings:

```yaml
microservice-configserver-config:
  environment:
    SPRING_PROFILES_ACTIVE: default
    SPRING_CONFIG_IMPORT: configserver:http://configserver:8071/
```

This tells `accounts`, `loans`, and `cards` to read config from the Config
Server container.

### Full Startup Order

1. Run Docker Compose:

   ```powershell
   docker compose up -d
   ```

2. Docker creates the `yadhuwanshirahulr` network.

3. RabbitMQ starts and becomes healthy.

4. Config Server starts after RabbitMQ is healthy.

5. Config Server clones or pulls configuration from Git.

6. Config Server exposes config APIs on port `8071`.

7. Accounts, Loans, and Cards start after Config Server is healthy.

8. Each business service calls Config Server using its application name and
   active profile.

9. Each service loads values like `build.version`, `accounts.message`,
   `loans.message`, or `cards.message`.

10. Services expose their APIs on host ports:

    ```text
    accounts -> http://localhost:8080
    loans    -> http://localhost:8090
    cards    -> http://localhost:9000
    config   -> http://localhost:8071
    rabbit   -> http://localhost:15672
    ```

### Useful Verification Commands

Check all containers:

```powershell
docker compose ps
```

Check Config Server health:

```powershell
curl http://localhost:8071/actuator/health/readiness
```

Check Accounts config value:

```powershell
curl http://localhost:8080/api/build-info
```

Check Accounts contact info:

```powershell
curl http://localhost:8080/api/contact-info
```

Check Config Server output directly:

```powershell
curl http://localhost:8071/accounts/default
```

Check logs:

```powershell
docker logs configServer-ms
docker logs accounts-ms
docker logs loans-ms
docker logs cards-ms
```

### Important Notes

- `configServer/pom.xml` builds the Config Server image
  `yadhuwanshirahulr/configserver:v2`.
- `accounts/pom.xml`, `loans/pom.xml`, and `cards/pom.xml` include
  `spring-cloud-starter-bus-amqp` so they can use RabbitMQ for Spring Cloud Bus.
- Inside Docker, services should use Compose service names such as
  `configserver` and `rabbit`.
- On the host machine, use `localhost` with the published ports.
- `localhost:8080` is Accounts, not Config Server.
- `localhost:8071` is Config Server.
- Compose `depends_on` with `condition: service_healthy` prevents services from
  starting before their dependencies are ready.
