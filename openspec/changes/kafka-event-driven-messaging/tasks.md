## 1. Infraestructura Kafka en Docker Compose

- [ ] 1.1 Agregar servicio `kafka` al `docker-compose.yml` con imagen `confluentinc/cp-kafka:7.7.1` en modo KRaft (`KAFKA_PROCESS_ROLES=broker,controller`), puerto 9092 expuesto al host
- [ ] 1.2 Configurar `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` para dev y crear manualmente los topics DLT en el entrypoint: `orders.DLT`, `users.DLT`
- [ ] 1.3 Agregar healthcheck al servicio kafka: `kafka-topics --bootstrap-server localhost:9092 --list` con retries
- [ ] 1.4 Configurar `depends_on: kafka` con condition `service_healthy` en order-service y notification-service

## 2. Dependencias y Configuración Spring Kafka

- [ ] 2.1 Agregar `spring-kafka` a `pom.xml` de `order-service` y `notification-service`
- [ ] 2.2 Configurar `application.yml` de cada servicio con `spring.kafka.bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` y propiedades de producer/consumer
- [ ] 2.3 Crear `KafkaProducerConfig` en order-service con `ProducerFactory<String, Object>` usando `JsonSerializer` y `KafkaTemplate` bean
- [ ] 2.4 Crear `KafkaConsumerConfig` en notification-service con `ConsumerFactory` usando `JsonDeserializer`, `enable.auto.commit=false`, y `KafkaListenerContainerFactory` con `DefaultErrorHandler`

## 3. Event DTOs Compartidos

- [ ] 3.1 Crear `OrderCreatedEvent` record Java en `order-service` con campos: `eventId` (UUID), `occurredAt` (Instant), `orderId` (Long), `userId` (Long), `totalAmount` (BigDecimal), `status` (String)
- [ ] 3.2 Crear `UserRegisteredEvent` record Java en `user-service` con: `eventId`, `occurredAt`, `userId`, `email`, `username`
- [ ] 3.3 Mover DTOs de eventos a módulo `shared/` del pom.xml padre para que notification-service los importe sin duplicar

## 4. Producers en order-service y user-service

- [ ] 4.1 Implementar `OrderEventPublisher` en order-service con método `publishOrderCreated(Order order)` que usa `kafkaTemplate.send("orders", order.getId().toString(), event)` y loguea el resultado con callback
- [ ] 4.2 Llamar a `OrderEventPublisher.publishOrderCreated()` en `OrderService.createOrder()` después del `orderRepository.save()`
- [ ] 4.3 Implementar `UserEventPublisher` en user-service análogo para topic `users` con `UserRegisteredEvent`

## 5. Consumer en notification-service

- [ ] 5.1 Implementar `OrderEventConsumer` con `@KafkaListener(topics = "orders", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")`
- [ ] 5.2 Implementar `NotificationService.sendOrderConfirmation(OrderCreatedEvent event)` que loguea la notificación (stub — sin integración real de email/SMS en el demo)
- [ ] 5.3 Configurar `DefaultErrorHandler` con `ExponentialBackOffWithMaxRetries(3)` y `DeadLetterPublishingRecoverer` apuntando a `orders.DLT`
- [ ] 5.4 Configurar `KafkaAdmin` bean con declaración de topics `orders.DLT` y `users.DLT` para auto-creación en startup

## 6. Verificación End-to-End

- [ ] 6.1 `docker compose up -d` sin errores — todos los servicios pasan healthcheck
- [ ] 6.2 `POST /api/orders` via API Gateway → order-service persiste y publica → notification-service loguea "Sending notification for order X" en sus logs
- [ ] 6.3 Verificar en logs de Kafka que el mensaje llega al topic `orders`: `docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning`
- [ ] 6.4 Simular fallo en notification-service (lanzar excepción en el consumer), verificar que tras 3 intentos el mensaje aparece en `orders.DLT`
