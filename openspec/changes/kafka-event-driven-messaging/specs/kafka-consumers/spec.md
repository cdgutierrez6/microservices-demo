## ADDED Requirements

### Requirement: @KafkaListener con consumer group por servicio
Los consumers SHALL usar `@KafkaListener(topics = "orders", groupId = "notification-service-group")` con deserialización JSON automática via `JsonDeserializer`. El consumer group ID SHALL seguir el patrón `<service-name>-group`.

#### Scenario: notification-service procesa OrderCreatedEvent
- **WHEN** order-service publica un `OrderCreatedEvent` al topic `orders`
- **THEN** `notification-service` recibe el evento en su `@KafkaListener`, lo deserializa a `OrderCreatedEvent` y llama a `NotificationService.sendOrderConfirmation(event)`

#### Scenario: Dos servicios consumen el mismo topic independientemente
- **WHEN** `notification-service` y un futuro `analytics-service` consumen ambos el topic `orders`
- **THEN** cada uno tiene su propio consumer group y ambos reciben todos los mensajes sin interferencia

---

### Requirement: Commit manual de offset post-procesamiento
Los consumers SHALL configurar `enable.auto.commit=false` y hacer commit explícito del offset solo después del procesamiento exitoso del mensaje.

#### Scenario: Procesamiento exitoso hace commit del offset
- **WHEN** `NotificationService.sendOrderConfirmation()` completa sin excepción
- **THEN** el consumer hace commit del offset y no reprocesa ese mensaje en el siguiente arranque

#### Scenario: Excepción en procesamiento no hace commit
- **WHEN** `sendOrderConfirmation()` lanza una excepción
- **THEN** el offset no se commitea y el mensaje es reprocesado tras los reintentos configurados

---

### Requirement: Deserialización tipada por topic
El `KafkaListenerContainerFactory` SHALL configurar un `ErrorHandlingDeserializer` que envuelve el `JsonDeserializer` para capturar errores de deserialización sin detener el consumer.

#### Scenario: Mensaje con formato inválido no mata el consumer
- **WHEN** llega un mensaje al topic `orders` con JSON malformado
- **THEN** el `ErrorHandlingDeserializer` lo captura, loguea el error, y el consumer continúa procesando el siguiente mensaje
