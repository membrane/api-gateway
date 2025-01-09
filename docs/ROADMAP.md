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
- log, print or message that writes to log. 
- Examples
  - soap/secured-wsdl
    - Check if we offer the sampleSoapService at www.predic8.de
    - Rewrite the example to use it
    - Readme
    - ExampleTest
  - REST / JSON API Versioning test
    - like examples/versioning/soap-xslt but much simpler with json

# Version 6.0.0

## TODOs

### Tests
- Run ConfigSerializationTest from annot
- List how to run all tests
  - ConfigSerializationTest
  - Examples
    - ExampleUnitTests, Without, With
- Is SessionResumptionTest still needed?

### Examples / Documentation
- Check
  - Is example embedded-java still running?
    - Update to Java 21 needed?
- Document <if>,<call>,<destination>
  - Write example with lots of samples in different languages
- Restructure samples
- Rename ExampleTests to .*ExampleTests
- Call Example
- Is still in use:
  - /xml/project.xml?
  
### Features

- Call of internal should be possible


### Internal
- XMLProtectionInterceptor.setFailResponse => Use ProblemDetails
- Rename service:// to internal://
- Delete interceptor/
  - Gatekeeper?
- proxies-2.xsd
  - new Namespace e.g. https://membrane-api.io...
- 2025
- ProblemDetails
  - Test for production mode filter
    - Return pd and check if answer contains pd.details ...
  - Return pretty to user
  - Should have right Content-Type
- Check AdminConsole
- Check that handlers define their flows

## Done
- Call plugin
- Example Tests without unzipping for every test
- Delete interceptor
  - api-management
  - XPathCBR aka choose
  - XPathExtractor
  - XPathInterceptor
- Swagger-proxy
  - OpenAPI supports Swagger 2.0
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer!
- Inactive UnitTests enabled


### Examples
- Implement
  - CustomErrorHandlingExampleTest
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

- Should AbstractHttpHandler and Http2ExchangeHandler have a common interface?

# Other

- Unfinshed or not referenced tests:
  - AcmeAzureTableApiStorageEngineTest (Deleted)
  - EtcdRequestTest (Deleted)
  - ConcurrentConnectionLimitTest
  - See UnitTests
  - HttpTransportTest
  - ...