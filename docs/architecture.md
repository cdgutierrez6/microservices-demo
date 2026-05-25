# Decisiones de Arquitectura

## ADR-001: Event-Driven con Apache Kafka

**Contexto:** Los microservicios necesitan comunicarse sin acoplamiento directo.

**Decisión:** Kafka como bus de eventos central, con topics por dominio.

**Consecuencias:** Alta disponibilidad, escalabilidad horizontal, complejidad de despliegue mayor.

## ADR-002: Base de Datos por Servicio (Database per Service)

**Contexto:** Cada microservicio debe ser autónomo y desacoplado.

**Decisión:** PostgreSQL dedicado por servicio (`orders_db`, `users_db`).

**Consecuencias:** Sin joins entre servicios. Consistencia eventual vía eventos.

## ADR-003: Outbox Pattern para Garantía de Entrega

**Contexto:** Necesitamos garantizar que los eventos se publiquen a Kafka aunque el servicio falle.

**Decisión:** Tabla `outbox_events` en la misma DB, publicador periódico (Debezium o scheduler).

**Consecuencias:** At-least-once delivery. Los consumidores deben ser idempotentes.

## ADR-004: JWT Stateless en API Gateway

**Contexto:** Los servicios necesitan saber quién hace la petición sin llamadas inter-servicio.

**Decisión:** JWT validado en el Gateway. Claims propagados via headers.

**Consecuencias:** Sin estado en los servicios. Revocación requiere lista negra en Redis.
