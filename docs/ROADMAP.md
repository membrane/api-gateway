# Membrane Roadmap

# Version 6.2.0

- One API that calls multiple Backends
  - Ideas
    - <target ../> inside <if>
- Routing with if instead of switch and cbr

# Version 6.1.0

- Update Java Version
- Change Interceptor chain from stack to instant execution
  - Probably in InterceptorFlowController {
- API Key Database Filestore
- Grafana Dashboard to import in examples/prometheus
  - Also provide the datasource config
  - Maybe the config can be included into the docker-compose setup
- Example Tests without unzipping for every test


# Version 6.0.0

- Delete interceptor/
  - api-management
  - CBR
  - Swagger-proxy
    - OpenAPI supports Swagger 2.0

- Check
  - Is example embeddug-java still running?
    - Update to Java 21 needed? 

- Move Examples
  - basic-xml-interceptor -> xml/basic-xml-interceptor
  - StaxInterceptor -> xml/stax-interceptor


- Refactor setHeader, setProperty make it like if
- Is SessionResumptionTest still needed?
- Document XPath Language of <if>
- Delete CBR
- Restructure samples
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer! 

- Simple JWT example 
- Clean up examples in examples/oauth2


