# Membrane Roadmap

# Version 6.2.0

- One API that calls multiple Backends
  - Ideas
    - <target ../> inside <if>
- Routing with if instead of switch and cbr
- <choose>
     <case test=""> // If this matches execute only nested
        <..interceptors>
     </case> 
     <case test=""></case> // Only evaluate if first did not match
     <case test=""></case>
     <otherwise></otherwise>
  </choose>

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
    - Rewrite the example to use it
    - Readme
    - ExampleTest
  - REST / JSON API Versioning test
    - like examples/versioning/soap-xslt but much simpler with json
- JdbcUserDataProvider
  - Migrate to PreparedStatement
- <target url="http://localhost:2000/${params.product}/>

# Version 6.0.0

## TODOs

### Tests
- List how to run all tests
  - ConfigSerializationTest
- Is SessionResumptionTest still needed?

### Examples / Documentation
- Check
  - Is example embedded-java still running?
    - Update to Java 21 needed?
- Document <if>,<call>,<destination>
  - Write example with lots of samples in different languages
- Restructure samples
- Rename ExampleTests to .*ExampleTests
- Is still in use:
  - /xml/project.xml?
  
### Features


### Internal
- XMLProtectionInterceptor.setFailResponse => Use ProblemDetails
- Rename service:// to internal://
- Delete interceptor/
  - Gatekeeper: Yes
  - SDY Speedy?
  - JsonPointer Extractor?
- proxies-2.xsd
  - new Namespace e.g. https://membrane-api.io...
- 2025
- Test in proxies.xml internal with port
- Interceptor init() and init(Router router)
- ProblemDetails
  - Validators
     - XML, JSON, WSDL
     - All in examples/validation
  - OpenAPI
- In com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService 
  - Remove throws: 
    - public abstract String getJwksEndpoint() throws Exception; 
- Exchange property name constants: See Exchange
- Dependencies
  - Log4J, where, what
- Interceptor Interface:
  - handle should not throw Exception
- AdminConsole
  - DisplayName: make all lower
- Cleaup:
  - LoggingContextInterceptor? Ask CG
- Merge log with print
  <log message="${header.foo}/>
  default: message="${header}\n${body}"
- Remove etcd stuff 
- Rewrite RatelimitInterceptor to use AbstractLanguageInterceptor
- Check that starter is really using Java 8

## Done
- Proxy.init() and init(router) make it clear what to call!
- Rename service:// to internal://
- Call plugin
- Example Tests without unzipping for every test
- Delete interceptor
  - api-management
  - XPathCBR aka choose
  - XPathExtractor
  - XPathInterceptor
  - Gatekeeper
- Swagger-proxy
  - OpenAPI supports Swagger 2.0
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer!
- Inactive UnitTests enabled
- ProblemDetails
  - Test for production mode filter
    - Return pd and check if answer contains pd.details ...


### Examples
- Rename Tests to *.ExampleTest
- Move Examples
  - basic-xml-interceptor -> xml/basic-xml-interceptor
  - StaxInterceptor -> xml/stax-interceptor


# Discussion

- ${} or #{} for expressions
  - Possible conflict with spring property placeholder configurer
  - Conflict with SpEL on startup? Are #{} replaced?
- For ADRs
  - Response Flow guarantee there is a response 
  - Request Flow guarantee there is a request
  - Return guarantee Response is there

- Wenn Exception/Abort passiert sofort Response mit Error setzen.

- Should AbstractHttpHandler and Http2ExchangeHandler have a common interface?
- What are nodeExceptions in Exchange? And nodeStatusCodes?
- Do we need RuleExchangeListener in RuleManager?

# Other

- Unfinshed or not referenced tests:
  - AcmeAzureTableApiStorageEngineTest (Deleted)
  - EtcdRequestTest (Deleted)
  - ConcurrentConnectionLimitTest
  - See UnitTests
  - HttpTransportTest
  - ...