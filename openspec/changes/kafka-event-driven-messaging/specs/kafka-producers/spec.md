## ADDED Requirements

### Requirement: KafkaTemplate para publicación de eventos
Los servicios publisher SHALL usar `KafkaTemplate<String, Object>` configurado con `JsonSerializer` para publicar eventos al topic correspondiente. La publicación SHALL ser asíncrona (`ListenableFuture` / `CompletableFuture`) y logueará el resultado (success/failure).

#### Scenario: OrderCreated publicado al topic orders
- **WHEN** `order-service` persiste un nuevo pedido exitosamente
- **THEN** `OrderEventPublisher.publish(OrderCreatedEvent)` envía el evento al topic `orders` con key `orderId` y el resultado se loguea con Slf4j

#### Scenario: Publicación fallida no bloquea la respuesta al cliente
- **WHEN** Kafka no está disponible momentáneamente
- **THEN** el producer reintenta internamente según `retries` config; si falla tras reintentos, loguea el error pero la API ya respondió al cliente

---

### Requirement: Event DTOs inmutables
Los DTOs de eventos SHALL ser records Java inmutables con todos los campos requeridos en el constructor. Cada evento incluye `eventId` (UUID), `occurredAt` (ISO timestamp), y los campos del dominio.

#### Scenario: OrderCreatedEvent serializado a JSON
- **WHEN** KafkaTemplate serializa un `OrderCreatedEvent`
- **THEN** el mensaje en Kafka contiene JSON con `eventId`, `occurredAt`, `orderId`, `userId`, `totalAmount`, `status`

---

### Requirement: Configuración externalizada de Kafka
La URL del broker Kafka SHALL ser configurable via `application.yml` (`spring.kafka.bootstrap-servers`) y sobreescribible por variable de entorno `SPRING_KAFKA_BOOTSTRAP_SERVERS` para Docker Compose.

#### Scenario: Servicio conecta a Kafka del compose
- **WHEN** el servicio arranca dentro de Docker Compose
- **THEN** usa `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092` del environment del compose y se conecta exitosamente
