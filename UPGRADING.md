# 3.5.X to 4.0 changes

## TODO

* documentation
* configuration of "other" (=non-HttpClientInterceptor) httpClients
* nice error message at startup if annotation processing is disabled
* 'transformerFactory' global bean: make local

### Decisions to be made

* writing config (admin interface, monitor, etc)
* rename @name (collides with spring)
* remove deprecated set...BeanId methods
* set @MCChildElement(allowForeign=true) by default

## Configuration

### monitor-beans.xml

* router/@adjustHostHeader has been moved to httpClient/@adjustHostHeader
* router/@indentMessage has been removed
* router/@adjustContentLength has been removed
* router/@trackExchange has been removed


### proxies.xml

* <global> element has been removed
  * global/router/@adjustContentLength is now always true (is there any use case where 'false' makes sense?)
  * global/monitor-gui has been removed (monitor config will be handled differently in 4.0)
  * global/proxyConfiguration has been moved to httpClient/proxyConfiguration

## Java API (major changes)

* @ElementName has been removed
