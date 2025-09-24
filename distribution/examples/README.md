# Working API Gateway Examples

Have **10 minutes** and want to solve a task with Membrane? These hands-on examples are ready to run and explore.

## [Deployment and Installation](deployment#deployment-and-installation)

* Docker 
* As Windows service

## [OpenAPI](openapi)

* Deploy APIs from OpenAPI definitions
* Validate requests, responses, and security

## [API Traffic Routing](routing-traffic#api-traffic-routing)

* Rewriting API URLs
* Shadow traffic to other environments (e.g., TEST)
* Content-based routing (JSON, XML)
* Dynamic routing with URI templates
* Internal routing
* Throttle API traffic

## [Scripting](scripting#scripting)

* Use Groovy and JavaScript for scripting

Samples of scripting with JSONPath, XPath and the expression language you can find in the [templating](templating#template---transforming-messages) or [orchestration](orchestration#orchestration) folders.

## [Security](security#samples-for-api-security-with-membrane-api-gateway)

* Authentication and authorization with JWT, OAuth2 and OIDC
* **API keys** and Basic Authentication
* Cross-Origin Resource Sharing (CORS)
* JSON protection
* SSL/TLS
* IP and host-based **Access Control Lists**
* Form login
* NTLM
* [Rate limiting](rate-limiting#rate-limiter) 

### [Message Validation](validation#message-validation)

* HTML-form validation
* JSON-Schema validation

## [Orchestration](orchestration#orchestration)

* Authentication with external APIs
* Samples for GET and POST callouts
* Use for-loops for multiple callouts

## [Extending Membrane](extending-membrane#configuration-and-extension-examples-for-membrane-api-gateway)

* Use `if` conditions to modify behavior
* Externalize configuration with **properties** and **environment variables**
* Build reusable **plugin chains** and create custom plugins
* How to embed Membrane into your Java applications 
* Error handling
* Store messages into files and databases
* Service discovery with `etcd`



## [Message Transformation](message-transformation#message-transformation)

* Create dynamic API responses using [templates](templating) (JSON, XML, Text)
* Generic JSON to XML and XML to JSON
* Replacing with Regex
* Transformation with Javascript

## [Legacy Integration for XML and Web Services](web-services-soap#web-services-soap)

### [XML](xml#xml-examples)

* XML validation and XSLT transformation
* Create XML plugins with DOM or StAX

### [Web Services with SOAP](web-services-soap#web-services-soap)

* Validation of SOAP messages against WSDL
* REST to SOAP conversion and migration
* Sample SOAP service for testing
* How to mock **SOAP services** 
* Add SOAP headers
* SOAP versioning

## Other Protocols

### [GraphQL](graphql#graphql)

* Validate GraphQL requests

### [Web Sockets](websockets#websockets)

* STOMP over Websockets
* Writing own Web Sockets plugins

## Operation

### [YAML Configuration](yaml-configuration#yaml-configuration)

### [Monitoring and Tracing](monitoring-tracing#monitoring-and-tracing)

* OpenTelemetry & Prometheus

### [Logging](logging#logging-requests-and-responses)

* Access log
* Log into console, CSV or databases
* Structured JSON logs

### [Load Balancing](loadbalancing#load-balancing-apis)

* Simple static setup
* Dynamic node management
* Session-based load balancing
* Backend health checks

## [API Testing](api-testing)
  
* API Greasing
