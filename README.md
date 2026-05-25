# Microservices Demo — Arquitectura Distribuida Enterprise

[![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat-square&logo=apache-kafka&logoColor=white)](https://kafka.apache.org)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io)

Implementación de referencia de una arquitectura de microservicios orientada a eventos, basada en patrones utilizados en sistemas de telemetría vehicular procesando **millones de eventos diarios en producción**.

---

## Arquitectura del Sistema

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

## Microservicios

### 1. `api-gateway` — Spring Cloud Gateway
- Enrutamiento dinámico a microservicios
- Rate limiting con Redis
- JWT validation centralizado
- Circuit breaker con Resilience4j

### 2. `order-service` — Gestión de Órdenes
- CRUD de órdenes con validaciones
- Publicador de eventos Kafka (`order.created`, `order.updated`)
- Base de datos: PostgreSQL
- Patrón Outbox para garantía de entrega

### 3. `user-service` — Gestión de Usuarios
- Registro, autenticación JWT
- Publicador de eventos (`user.registered`, `user.updated`)
- Cache con Redis
- Base de datos: PostgreSQL

### 4. `notification-service` — Notificaciones
- Consumidor Kafka multi-topic
- Envío de emails y push notifications
- Dead Letter Queue para reintentos

---

## Patrones Implementados

| Patrón | Descripción | Servicio |
|---|---|---|
| **Event Sourcing** | Eventos como fuente de verdad | Kafka |
| **Outbox Pattern** | Garantía de entrega at-least-once | order-service |
| **Saga (Choreography)** | Coordinación de transacciones distribuidas | Todos |
| **CQRS** | Separación lectura/escritura | order-service |
| **Circuit Breaker** | Tolerancia a fallos | api-gateway |
| **Cache-Aside** | Cache Redis para consultas frecuentes | user-service |
| **API Gateway** | Punto de entrada único | api-gateway |

---

## Inicio Rápido

### Prerrequisitos
- Docker Desktop
- Java 17+
- Maven 3.9+

### Levantar todo el stack

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

### Endpoints disponibles

```
GET  http://localhost:8080/orders          → Listar órdenes (vía Gateway)
POST http://localhost:8080/orders          → Crear orden
GET  http://localhost:8080/users           → Listar usuarios
POST http://localhost:8080/users/register  → Registrar usuario
POST http://localhost:8080/auth/login      → Obtener JWT
```

---

## Estructura del Proyecto

```
microservices-demo/
├── api-gateway/
│   ├── src/main/java/
│   │   └── com/cdgutierrez/gateway/
│   ├── pom.xml
│   └── Dockerfile
├── order-service/
│   ├── src/main/java/
│   │   └── com/cdgutierrez/orders/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── kafka/        ← producers y consumers
│   │       ├── outbox/       ← Outbox pattern
│   │       └── model/
│   ├── pom.xml
│   └── Dockerfile
├── user-service/
│   ├── src/main/java/
│   │   └── com/cdgutierrez/users/
│   ├── pom.xml
│   └── Dockerfile
├── notification-service/
│   ├── src/main/java/
│   │   └── com/cdgutierrez/notifications/
│   ├── pom.xml
│   └── Dockerfile
├── docker-compose.yml
├── docker-compose.dev.yml
└── docs/
    ├── architecture.md
    └── kafka-topics.md
```

---

## Docker Compose

```yaml
# Infraestructura base: Kafka + PostgreSQL + Redis
# Ver docker-compose.yml para configuración completa

services:
  zookeeper:     # Kafka coordination
  kafka:         # Message broker — 3 topics configurados
  postgres-orders:   # DB exclusiva para order-service
  postgres-users:    # DB exclusiva para user-service
  redis:         # Cache compartido
  kafka-ui:      # Visualización de topics en :8090
```

---

## Temas Kafka

| Topic | Particiones | Replication | Retención |
|---|---|---|---|
| `orders.created` | 3 | 1 | 7 días |
| `orders.updated` | 3 | 1 | 7 días |
| `user.registered` | 2 | 1 | 30 días |
| `notifications.pending` | 3 | 1 | 3 días |

---

## Tecnologías

- **Java 17** + **Spring Boot 3** + **Spring Cloud**
- **Apache Kafka** 3.x (event streaming)
- **PostgreSQL** 15 (persistencia)
- **Redis** 7 (cache, rate limiting)
- **Docker** + **Docker Compose**
- **Resilience4j** (circuit breaker, retry)
- **JWT** (autenticación stateless)
- **Flyway** (migraciones de base de datos)

---

## Contexto de Producción

Esta arquitectura está basada en patrones implementados en **SATRACK** (2022–2025), donde lideramos la evolución tecnológica de sistemas de telemetría vehicular procesando eventos GPS en tiempo real para flotas de vehículos a escala nacional.

---

## Autor

**Cristian Daniel Gutiérrez S.** — [LinkedIn](https://www.linkedin.com/in/cristian-daniel-guti%C3%A9rrez-segura) · [Portfolio](https://portafolio-frontend-wheat.vercel.app)
