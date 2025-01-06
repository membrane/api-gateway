# Membrane Roadmap

# Version 6.2.0

- One API that calls multiple Backends
  - Ideas
    - <target ../> inside <if>
- Routing with if instead of switch and cbr
- <if>...<else>

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
- List how to run all tests
  - ConfigSerializationTest
  - Examples
    - ExampleUnitTests, Without, With
- If/setHeader
  - Write example with lots of samples in different languages
- Delete interceptor/
   - Gatekeeper?
   - MethodOverrideInterceptor (keep)
   - SPDYInterceptor (?)
   - WADLInterceptor (keep)
   - WsaEndpointRewriterInterceptor (keep)
- Check
  - Is example embedded-java still running?
    - Update to Java 21 needed?
- Is SessionResumptionTest still needed?
- Document <if>,<call>,<destination>
- Restructure samples (TB)
- proxies-2.xsd
  - new Namespace e.g. https://membrane-api.io...
- 2025
- Rename ExampleTests to .*ExampleTests
- Examples
  - Call Example
- Is still in use:
  - /xml/project.xml?
- ProblemDetails
  - Test for production mode filter
    - Return pd and check if answer contains pd.details ...
- Rewrite ExampleTests with RestAssured: (BT)
  - Json2XmlExampleTest
- Check AdminConsole

## Done
- Call plugin
- Example Tests without unzipping for every test
- Deleted interceptors
  - api-management
    - ApiKeyCheckerInterceptor, AuthHead2BodyInterceptor
  - XPathCBR aka choose
  - XPathExtractor
  - XPathInterceptor
  - CacheInterceptor
  - DecoupledEndpointRewriterInterceptor
  - TestServiceInterceptor
- Swagger-proxy
  - OpenAPI supports Swagger 2.0
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer!
- Inactive UnitTests enabled


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