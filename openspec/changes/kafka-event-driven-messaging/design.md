## Context

El demo de microservicios necesita mostrar comunicación asíncrona real entre servicios independientes. Sin Kafka, order-service tendría que llamar a notification-service vía HTTP — acoplamiento directo, punto de fallo único, y latencia adicional en el flujo principal de creación de órdenes. Con Kafka, order-service publica el evento y continúa; notification-service lo procesa cuando puede.

## Goals / Non-Goals

**Goals:**
- Comunicación completamente asíncrona entre servicios via Kafka topics
- Cada servicio autónomo con su propio consumer group
- DLT por topic para mensajes fallidos — visibilidad operacional
- Configuración reproducible con Docker Compose

**Non-Goals:**
- Schema Registry (Avro) — JSON es suficiente para el demo
- Kafka Streams o KSQL para procesamiento de streams
- Exactamente-una-vez semántica (at-least-once es suficiente para notificaciones)
- Kafka UI o herramientas de monitoring (fuera de scope del demo base)

## Decisions

### D1 — KafkaTemplate de Spring Kafka sobre cliente Kafka nativo

**Decisión**: usar `KafkaTemplate<String, Object>` de Spring Kafka con serialización JSON automática.

**Alternativas consideradas**:
- _Cliente Kafka nativo (confluent-kafka-java)_: más control, pero requiere serialización/deserialización manual y configuración de bajo nivel — boilerplate innecesario para un demo.

**Rationale**: Spring Kafka abstrae la configuración del producer/consumer y provee `@KafkaListener` que reduce el código del consumer a un método anotado. La serialización JSON con `JsonSerializer` es suficiente para el demo.

---

### D2 — Consumer group ID por servicio sobre compartido

**Decisión**: `notification-service` usa consumer group `notification-service-group`; cada servicio tiene su propio group.

**Rationale**: consumer groups distintos garantizan que cada servicio procesa todos los mensajes del topic independientemente. Si notification-service y un hipotético analytics-service consumen `orders`, cada uno tiene su propio offset y procesamiento.

---

### D3 — DLT automático con Spring Kafka `DefaultErrorHandler`

**Decisión**: configurar `DefaultErrorHandler` con `DeadLetterPublishingRecoverer` que publica a `<topic>.DLT` tras N reintentos.

**Rationale**: Spring Kafka 2.8+ provee DLT out-of-the-box sin código adicional. El recoverer publica el mensaje fallido con headers de diagnóstico (excepción, stack trace, topic original). Configurable sin cambiar el código del consumer.

---

### D4 — Docker Compose para Kafka sin ZooKeeper (KRaft mode)

**Decisión**: usar imagen `confluentinc/cp-kafka` con `KAFKA_PROCESS_ROLES=broker,controller` (KRaft).

**Alternativas consideradas**:
- _Kafka + ZooKeeper separados_: requiere dos containers adicionales, ZooKeeper está deprecado desde Kafka 3.3.

**Rationale**: KRaft simplifica el compose a un solo container de Kafka. Para un demo educativo, esta es la configuración moderna y estándar.

## Risks / Trade-offs

| Riesgo | Mitigación |
|--------|-----------|
| Orden de arranque: Kafka debe estar listo antes que los servicios | `depends_on` + healthcheck en docker-compose; Spring Kafka reintenta la conexión automáticamente |
| Mensajes perdidos si notification-service está caído al llegar el evento | Kafka retiene los mensajes según `retention.ms`; el consumer los procesa al volver — at-least-once garantizado |
| JSON sin schema: cambios en el DTO rompen consumers | Convención de versionado: `OrderCreatedV1`, `OrderCreatedV2` como tipos distintos (schema evolution manual) |

## Migration Plan

Arranque completo:
1. `docker compose up -d` — levanta Kafka, PostgreSQL y los 4 servicios
2. Topics se crean automáticamente al primer produce (auto-create habilitado en dev)
3. `POST /api/orders` → order-service publica `OrderCreated` → notification-service lo consume y loguea

## Open Questions

- ¿Número de particiones para el topic `orders`? (propuesta: 3 para el demo — permite hasta 3 consumers en paralelo)
- ¿Retención de mensajes: 7 días (default) o reducir para el demo?
