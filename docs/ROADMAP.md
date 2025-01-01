# Membrane Roadmap



# Version 6.0.0

## TODOs
- If/setHeader
  - Write example with lots of samples in different languages


- Delete interceptor/
  - api-management
  - CBR - Replacement?
  - Swagger-proxy
    - OpenAPI supports Swagger 2.0

- Check
  - Is example embedded-java still running?
    - Update to Java 21 needed? 

- Move Examples
  - basic-xml-interceptor -> xml/basic-xml-interceptor
  - StaxInterceptor -> xml/stax-interceptor


- Is SessionResumptionTest still needed?
- Document XPath Language of <if>
- Restructure samples
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer! 

 - internalProxy ? Does not open a port <internal/>

- Error message when a targel url is misspelled: like (serive://i1)

## Done
- Refactor setHeader, setProperty make it like if

## Merge

Reliable resource loading (Windows)
Enhance bootup logging to include detailed rule information


Not in 6.0.0 and 5.8.X:
Add OAuth2redirectTest


# Version 6.1.0

- Grafana Dashboard to import in examples/prometheus
  - Also provide the datasource config
  - Maybe the config can be included into the docker-compose setup

- JWT with if, setHeader
  - e.g. Allow only POST for JWT with claim XYZ

- HttpClient, ...? Refactoring

# Version 6.2.0

- One API that calls multiple Backends
  - Ideas
    - <target ../> inside <if>
- Routing with if instead of switch and cbr

# Ideas
