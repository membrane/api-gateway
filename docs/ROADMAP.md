# Membrane Roadmap

# Version 6.2.0

- Plugin that uses AI e.g. Mock
- JSONBody
  - Store body as parsed JsonNode or Document
    - If JSON is needed by an interceptor use already parsed JSON
    
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
- Call Example
- new GroovyLanguageSupport() in GroocyExchangeExpression is instanciated all the time. Reuse it?

# Version 6.0.0

## TODOs

### Tests
- Is SessionResumptionTest still needed?

### Examples / Documentation
- Check
  - Is example embedded-java still running?
    - Update to Java 21 needed?
- Restructure samples
  
### Features


### Internal
- RuleManager:102 - API name is /products:2000. <api> names must not contain a '/'.
- Change maven coordinates?
- proxies-6.xsd
  - new Namespace e.g. https://membrane-api.io...6
- Dependencies TP, TB
  - Log4J, where, what
  - Updates
- '<spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">' 
  durch '<apiKeyFileStore .. />' ersetzen (dafür topLevel=true) BT

## Done
- Problem Details TB
  - component mandatory, subtype optional
  - Message from exception in message
    - Toplevel not in extension
    - optional
    - Message only from exception
      - Disable in builder
- Rewrite RatelimitInterceptor to use AbstractLanguageInterceptor TB
- Exchange property name constants: See Exchange TB
- Logging TB
  - Simple logger raus
  - JSON logging raus
- ProblemDetails
  - OpenAPI
  - XML, JSON, WSDL
- '<spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">' 
  durch '<apiKeyFileStore .. />' ersetzen (dafür topLevel=true) BT
- Test after next merges:
  12:23:17,049 ERROR 30 RouterThread /185.191.171.13:7356 SpELExchangeExpression:84 - EL1008E: Property or field 'exc' cannot be found on object of type 'com.predic8.membrane.core.lang.spel.SpELExchangeEvaluationContext' - maybe not public or not vali
- LogInterceptor:
  - Do not log body if Content-Encoding header is set 
    - Might be zip, br ...
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
- ProblemDetails: (TB)
  - When flow = RESPONSE it should always be an internal error!
- For ADRs
  - Response Flow guarantee there is a response 
  - Request Flow guarantee there is a request
  - Return guarantee Response is there

- Wenn Exception/Abort passiert sofort Response mit Error setzen.
- Do we need RuleExchangeListener in RuleManager?

# Other

- Not finished or not referenced tests:
  - ConcurrentConnectionLimitTest
  - See UnitTests
  - HttpTransportTest
  - ...