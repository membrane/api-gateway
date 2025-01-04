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
- Refactor HttpClient
  - Replace finalize with try(...)
- JWT Signer plugin
  - Signs the body if it is a JWT
  - JWT encoder
    - Encodes a JWT into binary version
- JSONBody 
  - Store body as parsed JsonNode or Document
    - If JSON is needed by an interceptor use already parsed JSON

# Version 6.0.0

## TODOs
- Run ConfigSerializationTest from annot
- List with all tests
  - Examples
    - ExampleUnitTests, Without, With
- If/setHeader
  - Write example with lots of samples in different languages
- Delete interceptor/
  - CBR - Replacement?
- Check
  - Is example embedded-java still running?
    - Update to Java 21 needed?
- Is SessionResumptionTest still needed?
- Document XPath Language of <if>
- Restructure samples
- Make <log headerOnly="false"/> clearer!
- proxies-2.xsd
- 2025
- Rename ExampleTests to .*ExampleTests
- Remove XPathExtractor

## Done
- Example Tests without unzipping for every test
- Delete interceptor
  - api-management
- Swagger-proxy
  - OpenAPI supports Swagger 2.0
- UnitTests: Use PackageScan for more or all

### Examples
- Rename Tests to *.ExampleTest
- Move Examples
  - basic-xml-interceptor -> xml/basic-xml-interceptor
  - StaxInterceptor -> xml/stax-interceptor


# Discussion

- K8S stuff document or delete?
- Excpetion Handling
  - throw Exception in Interceptor handle?
- Interceptor
  - init() and init(router)
    - Which one to call or both?

- For ADRs
  - Response Flow guarantee there is a response 
  - Request Flow guarantee there is a request
  - Return guarantee Response is there

- Wenn Exception/Abort passiert sofort Response mit Error setzen.


# Other

- Unfinshed or not referenced tests:
  - AcmeAzureTableApiStorageEngineTest (Deleted)
  - EtcdRequestTest (Deleted)
  - ConcurrentConnectionLimitTest
  - See UnitTests
  - HttpTransportTest
  - ...