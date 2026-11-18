---
type: feature
---
- `jmsConsumer`: Membrane can now consume messages from a JMS queue or topic. Every message runs through the flow and the target of the API like a request that came in over HTTP. A message is acknowledged when the target answers with a status below 400, including a redirect (3xx), which Membrane does not follow. Otherwise the broker delivers it again according to its redelivery policy and finally moves it to its dead-letter queue. The flow reads the header fields and properties of the message from `property.jms`, e.g. `${property.jms.messageId}`. A topic can be read with a durable shared subscription (`subscription`), and `concurrency` processes messages in parallel. Needs the Membrane JMS Extension
