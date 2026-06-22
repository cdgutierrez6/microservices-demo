## ADDED Requirements

### Requirement: DefaultErrorHandler con DeadLetterPublishingRecoverer
El consumer container SHALL configurar `DefaultErrorHandler` con `DeadLetterPublishingRecoverer` que publica mensajes fallidos a `<topic>.DLT` tras agotar los reintentos configurados (default: 3 intentos con backoff exponencial).

#### Scenario: Mensaje fallido va al DLT tras reintentos
- **WHEN** el procesamiento de un `OrderCreatedEvent` falla en los 3 intentos
- **THEN** el mensaje original es publicado a `orders.DLT` con headers `kafka_dlt-exception-message`, `kafka_dlt-original-topic`, y el consumer commitea el offset del mensaje original

#### Scenario: Consumer continúa tras enviar al DLT
- **WHEN** un mensaje es enviado al DLT
- **THEN** el consumer procesa el siguiente mensaje sin detenerse ni reiniciarse

---

### Requirement: Backoff exponencial entre reintentos
El `DefaultErrorHandler` SHALL configurar `ExponentialBackOffWithMaxRetries` con initial interval 1s, multiplier 2, y máximo 3 intentos — resultando en esperas de 1s, 2s antes del DLT.

#### Scenario: Reintentos con backoff exponencial
- **WHEN** el primer intento de procesamiento falla
- **THEN** el handler espera 1s antes del segundo intento, luego 2s antes del tercer intento, y tras el tercero publica al DLT

---

### Requirement: DLT topic existe antes del primer fallo
Los topics DLT (`orders.DLT`, `users.DLT`) SHALL crearse en el inicio de la aplicación via `@Bean KafkaAdmin` con los topics declarados, o pre-creados en el Docker Compose con `kafka-topics.sh`.

#### Scenario: DLT topic disponible en primer fallo
- **WHEN** ocurre el primer error de procesamiento en un entorno nuevo
- **THEN** el `DeadLetterPublishingRecoverer` puede publicar al DLT sin error de "topic no existe"
