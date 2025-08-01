# Configuration and Extension Examples for Membrane API Gateway

## Configuration and Deployment

* [Configuration properties and environment variables](configuration-properties)
  Prepare Membrane for production by externalizing configuration.

## Modifying API and Gateway Behavior

* [Conditional logic with `if`](if)
  Change API behavior based on conditions.
* [Global plugin chain](global-interceptor)
  Apply plugins globally across all APIs.
* [Plugin Chains](reusable-plugin-chains)
  Define plugin sequences once and reuse them in multiple APIs.
* [Error handling](error-handling)
  Transform backend error messages into a unified format.

## Saving API messages

* [File ExchangeStore](file-exchangestore)
  Persist messages to local disk.
* [MongoDB ExchangeStore](mongo-exchange-store)
  Store API messages in MongoDB for later inspection or analysis.

## Extending Membrane with Java

* [Custom plugins](custom-interceptor)
  Extend Membrane with your own interceptors in Java.
* [Embedding Membrane](embedding-java)
  Integrate Membrane directly into your Java application.

## Other

* [Service Discovery with `etcd`](service-discovery-with-etcd)
  Dynamically discover services and update routing.