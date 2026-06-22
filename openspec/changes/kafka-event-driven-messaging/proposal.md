## Why

En una arquitectura de microservicios, los servicios necesitan comunicarse sin acoplamiento directo: una llamada HTTP síncrona entre servicios crea dependencia de disponibilidad y degradación en cascada. Kafka como bus de mensajes asíncrono permite que cada servicio reaccione a eventos de dominio de otros servicios de forma desacoplada, con garantías de entrega y capacidad de replay.

## What Changes

- `order-service` publica eventos `OrderCreated` y `OrderStatusChanged` al topic `orders` cuando el estado de un pedido cambia
- `notification-service` consume el topic `orders` y envía notificaciones (email/SMS) ante cada evento de pedido
- `user-service` publica eventos `UserRegistered` al topic `users` para que otros servicios sincronicen datos de usuario
- Cada producer usa `KafkaTemplate` de Spring Kafka con serialización JSON
- Cada consumer usa `@KafkaListener` con consumer group ID por servicio
- Dead Letter Topic por topic principal para mensajes fallidos

## Capabilities

### New Capabilities

- `kafka-producers`: Producers Spring Kafka en order-service y user-service que publican eventos de dominio con KafkaTemplate
- `kafka-consumers`: Consumers con @KafkaListener en notification-service que reaccionan a eventos de pedidos
- `dead-letter-topics`: Manejo de mensajes fallidos con DLT por topic para visibilidad y replay operacional

### Modified Capabilities

_(ninguna — implementación inicial de la capa de mensajería event-driven)_

## Impact

- **`order-service/`**: `OrderEventPublisher`, `OrderEvent` DTO, configuración `KafkaProducerConfig`
- **`notification-service/`**: `OrderEventConsumer`, `NotificationService`, `KafkaConsumerConfig`
- **`user-service/`**: `UserEventPublisher`, `UserRegisteredEvent`
- **`docker-compose.yml`**: servicios `zookeeper` y `kafka` (o KRaft), topics pre-creados vía `kafka-topics.sh`
- **`pom.xml`**: dependencia `spring-kafka` en cada servicio producer/consumer
