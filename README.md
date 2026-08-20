# Microservices

## Spring Registry and Discovery — study notes

This project uses Netflix Eureka as a service registry. Eureka keeps a list of
running service instances, and the microservices use that list to find one
another by service name instead of hard-coding every host and port.

The important terms are:

- **Eureka Server:** the registry. It accepts registrations, heartbeats, and
  deregistration requests.
- **Eureka Client:** a microservice that registers itself and communicates with
  the registry.
- **Service instance:** one running copy of a service. For example, two
  `accounts` processes are two different instances.
- **Service ID:** the logical name used to identify a service, such as
  `ACCOUNTS`, `CARDS`, or `LOANS`.

## 1. The complete flow

Keep this sequence in mind when revising the topic:

```text
Config Server (8071)
        |
        v
Eureka Server (8070)
        ^
        |  register + heartbeat + deregister
        |
accounts (8080), cards (9000), loans (8090)
        |
        |  fetch registry and find services by name
        v
Service-to-service communication
```

The normal startup order is:

1. Start Config Server on port `8071`.
2. Start Eureka Server on port `8070`.
3. Start `accounts`, `cards`, and `loans`.
4. Each client registers with Eureka and starts sending heartbeats.
5. The clients fetch the registry and can discover other service instances.

Config Server is separate from Eureka. Config Server supplies configuration;
Eureka supplies the current locations of running service instances.

## 2. Creating the Eureka Server

The Eureka server module is located at `eurekaserver/eurekaserver`.

### Maven dependency

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

This starter provides the Eureka registry implementation and its HTTP API.

### Enabling the server

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaserverApplication.class, args);
    }
}
```

`@SpringBootApplication` starts the Spring Boot application. The important
annotation for discovery is `@EnableEurekaServer`; it turns this application
into the registry that clients can contact.

### Eureka server configuration

```yaml
spring:
  application:
    name: "eurekaserver"
  config:
    import: "optional:configserver:http://localhost:8071/"

management:
  endpoints:
    web:
      exposure:
        include: "*"
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
  endpoint:
    health:
      probes:
        enabled: true
```

The server's port is supplied by the configuration loaded from Config Server;
in this project it is expected to run on port `8070`. The optional Config Server
import means the Eureka application can still start if Config Server is not
available, provided it has enough local configuration.

## 3. Adding Eureka to a microservice

Each of `accounts`, `cards`, and `loans` has this dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

The starter makes the service a Eureka client. It gives the service the ability
to register itself, renew its registration, fetch the registry, and remove its
registration during shutdown.

## 4. Client configuration explained

The client configuration is the same conceptually in all three services:

```yaml
server:
  port: 8080                 # accounts; cards uses 9000, loans uses 8090

spring:
  application:
    name: "accounts"
  config:
    import: "optional:configserver:http://localhost:8071/"

eureka:
  instance:
    preferIpAddress: true
  client:
    fetchRegistry: true
    registerWithEureka: true
    serviceUrl:
      defaultZone: http://localhost:8070/eureka/
```

### `spring.application.name`

```yaml
spring:
  application:
    name: "accounts"
```

This is the logical service name. Eureka uses it to group instances. The
physical port is not the identity of the service; the name is.

The three names in this project are:

| Service | Port |
| --- | ---: |
| `accounts` | 8080 |
| `cards` | 9000 |
| `loans` | 8090 |

### `defaultZone`

```yaml
serviceUrl:
  defaultZone: http://localhost:8070/eureka/
```

This is the Eureka server endpoint. The client uses it for registration,
heartbeats, registry updates, and deregistration.

### `registerWithEureka`

```yaml
registerWithEureka: true
```

The service tells Eureka, “I am running and available.” Eureka then stores the
service ID, host/IP address, port, and status of this instance.

### `fetchRegistry`

```yaml
fetchRegistry: true
```

The client downloads the registry from Eureka and keeps a local copy/cache. A
service can use this information to locate another service by its logical name.

### `preferIpAddress`

```yaml
preferIpAddress: true
```

The instance advertises its IP address rather than relying on its hostname.
This is often useful in local development, containers, and environments where
hostname resolution is inconsistent.

## 5. Registration: what happens when a service starts?

For example, when `accounts` starts:

```text
1. Spring Boot starts accounts on port 8080.
2. Eureka client reads spring.application.name = accounts.
3. The client contacts http://localhost:8070/eureka/.
4. It sends a registration request containing its instance details.
5. Eureka stores accounts in its registry as an available instance.
6. accounts periodically renews that registration with a heartbeat.
```

Conceptually, the registration contains information like this:

```text
Application: ACCOUNTS
Host/IP:     127.0.0.1
Port:        8080
Status:      UP
Instance ID: a unique ID for this running process
```

The Eureka dashboard then shows the registered instance. Registration is not
the same as calling a business endpoint; it is metadata communication between
the client and the registry.

## 6. Heartbeats: how Eureka knows a service is alive

Registration alone is not enough. A process may register successfully and then
crash. Therefore, the Eureka client sends a renewal request, commonly called a
heartbeat, at a regular interval.

```text
accounts  ---- heartbeat/renewal ---->  Eureka Server
accounts  <--- successful response ---  Eureka Server
```

The default Eureka client heartbeat interval is commonly 30 seconds. The exact
interval can be changed with client configuration, for example:

```yaml
eureka:
  instance:
    leaseRenewalIntervalInSeconds: 30
    leaseExpirationDurationInSeconds: 90
```

`leaseRenewalIntervalInSeconds` controls how often the client renews its lease.
`leaseExpirationDurationInSeconds` controls how long Eureka can go without a
renewal before considering the instance expired. These values are shown here
for learning; the current project uses Eureka defaults because they are not
explicitly configured.

If a service stops sending heartbeats because it crashed, lost its network
connection, or was killed, Eureka eventually removes it after the lease expires.
This is called eviction. Eureka also has self-preservation behavior to avoid
removing many instances during a temporary network failure, so removal may not
be immediate in every environment.

Important distinction:

```text
Heartbeat  = “I am still alive; keep my registration.”
Registration = “Here are my service details.”
Eviction    = “The lease expired; remove this apparently dead instance.”
```

## 7. Discovery: how a service finds another service

With discovery, callers use a service name rather than a fixed address:

```text
accounts -> find service named CARDS in its Eureka registry cache
         -> receive one or more cards instances
         -> call the selected instance
```

Without discovery, a caller might be forced to use:

```text
http://localhost:9000/cards
```

That becomes fragile when the port, host, or number of instances changes. With
Eureka, the location is registered dynamically. If multiple instances exist,
the client-side load-balancing layer can select an available instance. Eureka
itself is the registry; it does not implement the business request.

## 8. Graceful shutdown and deregistration

When a client shuts down gracefully, its Spring application context closes.
The Eureka client receives that shutdown event and sends a cancellation request
to Eureka. The cancellation tells the server to remove that exact instance
immediately instead of waiting for its heartbeat lease to expire.

The project exposes Spring Boot Actuator endpoints and permits shutdown:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    shutdown:
      access: unrestricted
```

The shutdown request is:

```http
POST http://localhost:8080/actuator/shutdown
```

For the other services, use their ports:

```http
POST http://localhost:9000/actuator/shutdown  # cards
POST http://localhost:8090/actuator/shutdown  # loans
```

The expected flow is:

```text
POST /actuator/shutdown
        |
        v
Spring closes the application context
        |
        v
Eureka client sends cancellation/deregistration
        |
        v
Eureka removes the instance from the registry
        |
        v
New callers no longer receive that instance
```

If the process is force-killed or the machine fails, the shutdown callback may
not run. In that case, no cancellation is sent and Eureka relies on missed
heartbeats plus lease expiration/eviction to remove the instance.

For production systems, exposing shutdown without authentication is unsafe.
The current setting is useful for local learning only; it should be protected
or disabled in a real deployment.

## 9. Actuator `info` metadata

Each client also exposes application information:

```yaml
management:
  info:
    env:
      enabled: true

info:
  app:
    name: "accounts"
    description: "Secure Bank Account Applications"
    version: "1.0.0"
```

This metadata is available through `/actuator/info`. It helps identify the
running application during monitoring and debugging. It is separate from the
Eureka registration, although both describe the running service.

## 10. What to remember

```text
Start service
  -> register with Eureka
  -> send heartbeats
  -> remain available in the registry
  -> fetch registry for discovery

Graceful shutdown
  -> send deregistration/cancel request
  -> Eureka removes instance immediately

Unexpected failure
  -> no deregistration request
  -> heartbeats stop
  -> lease expires
  -> Eureka eventually evicts the instance
```

The core reason for using Eureka is dynamic service location. Services can be
started, stopped, moved, or scaled without changing every caller's hard-coded
URL.

#New Line to check gitlab and github
