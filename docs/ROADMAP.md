# Membrane Roadmap

# Version 6.2.0

- One API that calls multiple Backends
  - Ideas
    - <target ../> inside <if>
- Routing with if instead of switch and cbr

# Version 6.1.0

- Grafana Dashboard to import in examples/prometheus
  - Also provide the datasource config
  - Maybe the config can be included into the docker-compose setup
- Example Tests without unzipping for every test
- Refactor HttpClient
  - Replace finalize with try(...)

# Version 6.0.0

## TODOs
- Delete SSLProxy

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



- Is SessionResumptionTest still needed?
- Document XPath Language of <if>
- Restructure samples
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer! 

### Examples
- Rename Tests to *.ExampleTest
- Move Examples
  - basic-xml-interceptor -> xml/basic-xml-interceptor
  - StaxInterceptor -> xml/stax-interceptor


# Discussion

- K8S stuff document or delete?

# Other

- Unfinshed or not referenced tests:
  - AcmeAzureTableApiStorageEngineTest (Deleted)
  - EtcdRequestTest (Deleted)
  - ConcurrentConnectionLimitTest
  - See UnitTests
  - HttpTransportTest
  - ...