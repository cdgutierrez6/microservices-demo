# Microservices Demo — Enterprise Distributed Architecture

[![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat-square&logo=apache-kafka&logoColor=white)](https://kafka.apache.org)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io)

---

<details open>
<summary><h2>🇺🇸 English</h2></summary>

Reference implementation of an event-driven microservices architecture, based on patterns used in vehicle telemetry systems processing **millions of daily events in production**.

---

### System Architecture

```
                          ┌─────────────────────────────────────────────────┐
                          │                  API GATEWAY                    │
                          │         (Spring Cloud Gateway :8080)            │
                          └──────────┬──────────────┬───────────────────────┘
                                     │              │
                    ┌────────────────▼──┐    ┌──────▼────────────────┐
                    │  ORDER SERVICE    │    │   USER SERVICE        │
                    │  (Java/Spring)    │    │   (Java/Spring)       │
                    │  :8081            │    │   :8082               │
                    └──────────┬────────┘    └──────┬────────────────┘
                               │                    │
                               ▼                    ▼
                    ┌─────────────────────────────────────────┐
                    │           APACHE KAFKA                  │
                    │     orders-topic | user-events          │
                    │     notifications-topic                 │
                    └──────────┬──────────────────────────────┘
                               │
                    ┌──────────▼────────────────┐
                    │   NOTIFICATION SERVICE    │
                    │   (Consumer/Producer)     │
                    │   :8083                   │
                    └───────────────────────────┘

  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
  │  PostgreSQL      │   │  PostgreSQL      │   │  Redis Cache    │
  │  orders_db       │   │  users_db        │   │  (Sessions)     │
  └─────────────────┘   └─────────────────┘   └─────────────────┘
```

---

### Microservices

#### 1. `api-gateway` — Spring Cloud Gateway
- Dynamic routing to downstream microservices
- IP-based rate limiting with Redis (100 req/s)
- Centralized JWT validation
- Circuit breaker with Resilience4j per route
- Correlation ID injection via `LoggingFilter`

#### 2. `order-service` — Order Management
- Full order lifecycle with state machine (PENDING → CONFIRMED → SHIPPED → DELIVERED)
- Kafka event publisher (`order.created`, `order.updated`) with **Outbox Pattern**
- Idempotent producer (acks=all, retries=3) for exactly-once guarantee
- Database: PostgreSQL with Flyway migrations

#### 3. `user-service` — User Management
- Registration + JWT authentication (JJWT, HMAC-SHA256)
- Kafka event publisher (`user.registered`, `user.updated`)
- Redis Cache-Aside (10 min TTL) for frequent queries
- Database: PostgreSQL

#### 4. `notification-service` — Notifications
- Multi-topic Kafka consumer (MANUAL_IMMEDIATE ack)
- 3 concurrent consumers for throughput
- Dispatcher pattern for flexible notification routing

---

### Patterns Implemented

| Pattern | Description | Service |
|---|---|---|
| **Outbox Pattern** | At-least-once delivery guarantee | order-service |
| **Saga (Choreography)** | Distributed transaction coordination | All services |
| **Circuit Breaker** | Fault tolerance, fallback responses | api-gateway |
| **Cache-Aside** | Redis cache for frequent reads | user-service |
| **API Gateway** | Single entry point, cross-cutting concerns | api-gateway |
| **CQRS** | Read/write separation | order-service |
| **Correlation ID** | Distributed tracing across services | api-gateway |

---

### Quick Start

#### Prerequisites
- Docker Desktop
- Java 17+
- Maven 3.9+

#### Start the full stack

```bash
# Clone the repository
git clone https://github.com/cdgutierrez6/microservices-demo.git
cd microservices-demo

# Start infrastructure (Kafka, PostgreSQL, Redis, Zookeeper)
docker-compose up -d kafka postgres-orders postgres-users redis

# Wait for Kafka to be ready (~20s), then start all services
docker-compose up -d

# Verify running services
docker-compose ps
```

#### Available Endpoints

```
GET  http://localhost:8080/orders          → List orders (via Gateway)
POST http://localhost:8080/orders          → Create order
GET  http://localhost:8080/users           → List users
POST http://localhost:8080/users/register  → Register user
POST http://localhost:8080/auth/login      → Get JWT token
```

---

### Project Structure

```
microservices-demo/
├── api-gateway/
│   ├── src/main/java/com/cdgutierrez/gateway/
│   │   ├── config/           # Routes, CircuitBreaker, RateLimiter
│   │   ├── filter/           # LoggingFilter (Correlation ID)
│   │   └── controller/       # FallbackController
│   ├── pom.xml
│   └── Dockerfile
├── order-service/
│   ├── src/main/java/com/cdgutierrez/orders/
│   │   ├── controller/       # OrderController (5 endpoints)
│   │   ├── service/          # OrderService + transactional logic
│   │   ├── repository/       # OrderRepository, OutboxRepository
│   │   ├── kafka/            # OrderEventProducer (@Scheduled relay)
│   │   ├── outbox/           # OutboxEvent entity + Outbox Pattern
│   │   └── model/            # Order, OrderItem (state machine)
│   ├── src/main/resources/db/migration/  # Flyway V1
│   ├── pom.xml
│   └── Dockerfile
├── user-service/
│   ├── src/main/java/com/cdgutierrez/users/
│   │   ├── controller/       # UserController (register, login, getById)
│   │   ├── service/          # UserService (cache-aside + Kafka)
│   │   ├── security/         # JwtService (JJWT HMAC-SHA256)
│   │   └── model/            # User entity
│   ├── pom.xml
│   └── Dockerfile
├── notification-service/
│   ├── src/main/java/com/cdgutierrez/notifications/
│   │   ├── consumer/         # OrderEventConsumer (MANUAL_IMMEDIATE ack)
│   │   ├── service/          # NotificationService (dispatcher)
│   │   └── config/           # KafkaConfig (3 concurrent consumers)
│   ├── pom.xml
│   └── Dockerfile
├── docker-compose.yml
└── pom.xml                   # Parent POM (Spring Boot 3.3.4, Java 17)
```

---

### Kafka Topics

| Topic | Partitions | Replication | Retention |
|---|---|---|---|
| `orders.created` | 3 | 1 | 7 days |
| `orders.updated` | 3 | 1 | 7 days |
| `user.registered` | 2 | 1 | 30 days |
| `notifications.pending` | 3 | 1 | 3 days |

---

### Technologies

- **Java 17** + **Spring Boot 3** + **Spring Cloud**
- **Apache Kafka** 3.x (event streaming)
- **PostgreSQL** 15 (persistence per service)
- **Redis** 7 (cache + rate limiting)
- **Docker** + **Docker Compose**
- **Resilience4j** (circuit breaker, retry)
- **JJWT** (stateless JWT authentication)
- **Flyway** (database migrations)

---

### Production Context

This architecture is based on patterns implemented at **SATRACK** (2022–2025), where I led the technological evolution of vehicle telemetry systems processing real-time GPS events for national-scale vehicle fleets.

---

### Author

**Cristian Daniel Gutiérrez S.** — Solutions Architect | Senior Java Engineer

[LinkedIn](https://www.linkedin.com/in/cristian-daniel-guti%C3%A9rrez-segura) · [Portfolio](https://portafolio-frontend-wheat.vercel.app) · [cdgutierrez6@gmail.com](mailto:cdgutierrez6@gmail.com)

</details>

---

<details>
<summary><h2>🇨🇴 Español</h2></summary>

Implementación de referencia de una arquitectura de microservicios orientada a eventos, basada en patrones utilizados en sistemas de telemetría vehicular procesando **millones de eventos diarios en producción**.

---

### Arquitectura del Sistema

```
                          ┌─────────────────────────────────────────────────┐
                          │                  API GATEWAY                    │
                          │         (Spring Cloud Gateway :8080)            │
                          └──────────┬──────────────┬───────────────────────┘
                                     │              │
                    ┌────────────────▼──┐    ┌──────▼────────────────┐
                    │  ORDER SERVICE    │    │   USER SERVICE        │
                    │  (Java/Spring)    │    │   (Java/Spring)       │
                    │  :8081            │    │   :8082               │
                    └──────────┬────────┘    └──────┬────────────────┘
                               │                    │
                               ▼                    ▼
                    ┌─────────────────────────────────────────┐
                    │           APACHE KAFKA                  │
                    │     orders-topic | user-events          │
                    │     notifications-topic                 │
                    └──────────┬──────────────────────────────┘
                               │
                    ┌──────────▼────────────────┐
                    │   NOTIFICATION SERVICE    │
                    │   (Consumer/Producer)     │
                    │   :8083                   │
                    └───────────────────────────┘

  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
  │  PostgreSQL      │   │  PostgreSQL      │   │  Redis Cache    │
  │  orders_db       │   │  users_db        │   │  (Sessions)     │
  └─────────────────┘   └─────────────────┘   └─────────────────┘
```

---

### Microservicios

#### 1. `api-gateway` — Spring Cloud Gateway
- Enrutamiento dinámico a microservicios
- Rate limiting por IP con Redis (100 req/s)
- Validación JWT centralizada
- Circuit breaker con Resilience4j por ruta
- Inyección de Correlation ID vía `LoggingFilter`

#### 2. `order-service` — Gestión de Órdenes
- Ciclo de vida completo con máquina de estados (PENDING → CONFIRMED → SHIPPED → DELIVERED)
- Publicador Kafka (`order.created`, `order.updated`) con **Outbox Pattern**
- Productor idempotente (acks=all, retries=3) para garantía exactly-once
- Base de datos: PostgreSQL con migraciones Flyway

#### 3. `user-service` — Gestión de Usuarios
- Registro + autenticación JWT (JJWT, HMAC-SHA256)
- Publicador Kafka (`user.registered`, `user.updated`)
- Cache-Aside con Redis (TTL 10 min) para consultas frecuentes
- Base de datos: PostgreSQL

#### 4. `notification-service` — Notificaciones
- Consumer Kafka multi-topic (ack MANUAL_IMMEDIATE)
- 3 consumers concurrentes para mayor throughput
- Patrón Dispatcher para routing flexible de notificaciones

---

### Patrones Implementados

| Patrón | Descripción | Servicio |
|---|---|---|
| **Outbox Pattern** | Garantía de entrega at-least-once | order-service |
| **Saga (Choreography)** | Coordinación de transacciones distribuidas | Todos |
| **Circuit Breaker** | Tolerancia a fallos, respuestas fallback | api-gateway |
| **Cache-Aside** | Cache Redis para lecturas frecuentes | user-service |
| **API Gateway** | Punto de entrada único, cross-cutting | api-gateway |
| **CQRS** | Separación lectura/escritura | order-service |
| **Correlation ID** | Trazabilidad distribuida entre servicios | api-gateway |

---

### Inicio Rápido

#### Prerrequisitos
- Docker Desktop
- Java 17+
- Maven 3.9+

#### Levantar todo el stack

```bash
# Clonar el repositorio
git clone https://github.com/cdgutierrez6/microservices-demo.git
cd microservices-demo

# Levantar infraestructura (Kafka, PostgreSQL, Redis, Zookeeper)
docker-compose up -d kafka postgres-orders postgres-users redis

# Esperar que Kafka esté listo (~20s) y levantar servicios
docker-compose up -d

# Verificar servicios activos
docker-compose ps
```

#### Endpoints disponibles

```
GET  http://localhost:8080/orders          → Listar órdenes (vía Gateway)
POST http://localhost:8080/orders          → Crear orden
GET  http://localhost:8080/users           → Listar usuarios
POST http://localhost:8080/users/register  → Registrar usuario
POST http://localhost:8080/auth/login      → Obtener JWT
```

---

### Estructura del Proyecto

```
microservices-demo/
├── api-gateway/
│   ├── src/main/java/com/cdgutierrez/gateway/
│   │   ├── config/           # Rutas, CircuitBreaker, RateLimiter
│   │   ├── filter/           # LoggingFilter (Correlation ID)
│   │   └── controller/       # FallbackController
│   ├── pom.xml
│   └── Dockerfile
├── order-service/
│   ├── src/main/java/com/cdgutierrez/orders/
│   │   ├── controller/       # OrderController (5 endpoints)
│   │   ├── service/          # OrderService + lógica transaccional
│   │   ├── repository/       # OrderRepository, OutboxRepository
│   │   ├── kafka/            # OrderEventProducer (@Scheduled relay)
│   │   ├── outbox/           # OutboxEvent + Outbox Pattern
│   │   └── model/            # Order, OrderItem (máquina de estados)
│   ├── src/main/resources/db/migration/  # Flyway V1
│   ├── pom.xml
│   └── Dockerfile
├── user-service/
│   ├── src/main/java/com/cdgutierrez/users/
│   │   ├── controller/       # UserController (register, login, getById)
│   │   ├── service/          # UserService (cache-aside + Kafka)
│   │   ├── security/         # JwtService (JJWT HMAC-SHA256)
│   │   └── model/            # User entity
│   ├── pom.xml
│   └── Dockerfile
├── notification-service/
│   ├── src/main/java/com/cdgutierrez/notifications/
│   │   ├── consumer/         # OrderEventConsumer (ack MANUAL_IMMEDIATE)
│   │   ├── service/          # NotificationService (dispatcher)
│   │   └── config/           # KafkaConfig (3 consumers concurrentes)
│   ├── pom.xml
│   └── Dockerfile
├── docker-compose.yml
└── pom.xml                   # POM padre (Spring Boot 3.3.4, Java 17)
```

---

### Temas Kafka

| Topic | Particiones | Replication | Retención |
|---|---|---|---|
| `orders.created` | 3 | 1 | 7 días |
| `orders.updated` | 3 | 1 | 7 días |
| `user.registered` | 2 | 1 | 30 días |
| `notifications.pending` | 3 | 1 | 3 días |

---

### Tecnologías

- **Java 17** + **Spring Boot 3** + **Spring Cloud**
- **Apache Kafka** 3.x (event streaming)
- **PostgreSQL** 15 (persistencia por servicio)
- **Redis** 7 (cache + rate limiting)
- **Docker** + **Docker Compose**
- **Resilience4j** (circuit breaker, retry)
- **JJWT** (autenticación JWT stateless)
- **Flyway** (migraciones de base de datos)

---

### Contexto de Producción

Esta arquitectura está basada en patrones implementados en **SATRACK** (2022–2025), donde lideré la evolución tecnológica de sistemas de telemetría vehicular procesando eventos GPS en tiempo real para flotas a escala nacional.

---

### Autor

**Cristian Daniel Gutiérrez S.** — Solutions Architect | Senior Java Engineer

[LinkedIn](https://www.linkedin.com/in/cristian-daniel-guti%C3%A9rrez-segura) · [Portfolio](https://portafolio-frontend-wheat.vercel.app) · [cdgutierrez6@gmail.com](mailto:cdgutierrez6@gmail.com)

</details>
