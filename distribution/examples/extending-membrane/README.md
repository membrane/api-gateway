# Configuration and Extension Examples for Membrane API Gateway

These examples show how to adapt, customize, and embed Membrane for real-world scenarios beyond basic reverse-proxy usage.

## Modifying API and Gateway Behavior

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