---
type: feature
---
- `jmsProducer`: header fields and properties of the message can be set with `correlationId`, `type`, `replyTo`, `priority`, `timeToLive`, `deliveryDelay` and `properties`. Values can contain expressions, and a single expression keeps its type, e.g. a number. After sending, `property.jmsSent` describes the sent message, e.g. `${property.jmsSent.messageId}`. Needs version 1.1.0 of the Membrane JMS Extension
