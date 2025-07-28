# Membrane Roadmap

# 7.0.0

- HttpClient
  - Change Signature: public Exchange call(Exchange exc) throws Exception 
    =>  public void call(Exchange exc) throws Exception {

# 6.5.0

- Data Masking
  - Is JSONPath replacement with Jayway possible? <mask>$.cusomter.payment.creditcard
    - Other ways to do it.
- <apiKey/>
    <scriptXX>${json[key]}</scriptXX>
  - See: RateLimitInterceptor

# 6.4.0

- Refactor MessageUtil
  - MessageUtil: Remove getXRequest and use Request Builder instead
- Refactor: Cookie maybe centralize Cookie Handling in a Cookie class


# 6.3.0

- JSONBody
  - Store body as parsed JsonNode or Document
    - If JSON is needed by an interceptor use already parsed JSON
- READMEs in example folders listing the examples
- Grafana Dashboard to import in examples/prometheus
  - Also provide the datasource config
  - Maybe the config can be included into the docker-compose setup
- Refactor HttpClient
  - Replace finalize with try(...)
- JdbcUserDataProvider
  - Migrate to PreparedStatement
- OAuth2 refactoring
  - In Interface com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService
    - Remove throws:
      - public abstract String getJwksEndpoint() throws Exception;
      - public abstract void init() throws Exception;
      - getEndSessionEndpoint() throws Exception
      - doDynamicRegistration(List<String> callbackURLs) throws Exception
- SampleSOAPService: Add some more cities
- Refactor: interceptor.session

### Internal
- proxies-6.xsd
  - new Namespace e.g. https://membrane-api.io...6
- '<spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">' 
  durch '<apiKeyFileStore .. />' ersetzen (dafür topLevel=true) BT

# Discussion

- <api> without port => Change from port 80 to matches all open ports
- ProblemDetails: (TB)
  - When flow = RESPONSE it should always be an internal error!
- For ADRs
  - Response Flow guarantee there is a response 
  - Request Flow guarantee there is a request
  - Return guarantee Response is there

- Wenn Exception/Abort passiert sofort Response mit Error setzen.
