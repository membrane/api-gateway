# Extending the API Gateway

## Configuration and Deployment

* [Configuration properties and environment variables](configuration-properties)
  Setup Membrane for production environments.

## Modifying the Behaviour of APIs and the Gateway

* [Conditional with `if`](if)
  Make your APIs behave differently based on certain conditions
* [Global plugin chain](global-interceptor)
  Engage plugins globally for all deployed APIs
* [Plugin Chains](reusable-plugin-chains)
  Reuse plugin configurations across multiple APIs
* [Error handling](error-handling)
  How to transform proprietary error messages from backends into your own format

## Saving API messages

* [File ExchangeStore](file-exchangestore)
  Save messages into a folder.
* [MongoDB ExchangeStore](mongo-exchange-store)
  Save messages into a MongoDB database

## Extending with Java

* [Writing custom plugins](custom-interceptor)
  How to extend the gateway with your own interceptor written in Java
* [Embedding Membrane](embedding-java)
  How to embed an API Gateway into Java applications

## Other

* [Service Discovery](service-discovery-with-etcd)
  Discover backend services with the `etcd` registry