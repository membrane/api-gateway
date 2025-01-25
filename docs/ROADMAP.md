# Membrane Roadmap

# Version 6.2.0

- Plugin that uses AI e.g. Mock
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
- REST / JSON API Versioning test
  - like examples/versioning/soap-xslt but much simpler with json
- JdbcUserDataProvider
  - Migrate to PreparedStatement
- <target url="http://localhost:2000/${params.product}/>
- OAuth2 refactoring
  - In Interface com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService
    - Remove throws:
      - public abstract String getJwksEndpoint() throws Exception;
      - public abstract void init() throws Exception;
      - getEndSessionEndpoint() throws Exception
      - doDynamicRegistration(List<String> callbackURLs) throws Exception

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
- Call Example
- Is still in use:
  - /xml/project.xml?
  
### Features


### Internal
- RuleManager:102 - API name is /products:2000. <api> names must not contain a '/'.
- Change maven coordinates?
- proxies-6.xsd
  - new Namespace e.g. https://membrane-api.io...6
- ProblemDetails TB
  - Validators
     - XML, JSON, WSDL
     - All in examples/validation
  - OpenAPI
- Exchange property name constants: See Exchange TB
- Logging TB
  - Simple logger raus
  - JSON logging raus
- Dependencies TP, TB
  - Log4J, where, what
  - Updates
- Rewrite RatelimitInterceptor to use AbstractLanguageInterceptor TB
- Language TB
  - Exchange expression 
    - getExpression
  - AbstractLanguageInterceptor as Interface
- Problem Details TB
  - component mandatory, subtype optional
  - Message from exception in message
    - Toplevel not in extension
    - optional
    - Message only from exception
      - Disable in builder
- '<spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">' 
  durch '<apiKeyFileStore .. />' ersetzen (dafür topLevel=true) BT

## Done
- Merge log with print
- XMLProtectionInterceptor.setFailResponse => Use ProblemDetails
- Rename ExampleTests to .*ExampleTests
- Examples
  - soap/secured-wsdl
    - Rewrite the example to use it
    - ExampleTest
- log, print or message that writes to log.
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

- <api> without port => Change from port 80 to matches all open ports
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