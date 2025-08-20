# Membrane Roadmap

# 7.0.0

- HttpClient
  - Change Signature: public Exchange call(Exchange exc) throws Exception
    =>  public void call(Exchange exc) throws Exception {
- Remove HttpClientInterceptor.setAdjustHeader(boolean) it is already in HttpClientConfiguration

# 6.5.0

- Data Masking
  - Is JSONPath replacement with Jayway possible? <mask>$.cusomter.payment.creditcard
    - Other ways to do it.
- <apiKey/>
    <scriptXX>${json[key]}</scriptXX>
  - See: RateLimitInterceptor

# 6.4.0

- Refactor: Cookie maybe centralize Cookie Handling in a Cookie class
- Loadbalancing description with pacemaker
- JSONBody
  - Store body as parsed JsonNode or Document
    - If JSON is needed by an interceptor use already parsed JSON
- JdbcUserDataProvider
  - Migrate to PreparedStatement
- OAuth2 refactoring
  - In Interface com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService
    - Remove throws:
      - public abstract String getJwksEndpoint() throws Exception;
      - public abstract void init() throws Exception;
      - getEndSessionEndpoint() throws Exception
      - doDynamicRegistration(List<String> callbackURLs) throws Exception

# 6.3.0

- Describe RPM Setup
- examples/routing-traffic/outgoing-api-gateway (TB)
- Cook Book: outgoing-api-gateway (TB)
- Template/Static Interceptor: Refactor (TB) 
  - one protected method to overwrite for byte[] content
  - Prettify logic only once
- Template/Static Interceptor: Pretty for text/* (Refactor first) (TB)
  - Pretty on text should trim whitespace incl. linebreaks at start and end
- READMEs in example folders listing the examples (TB)
- Refactor HttpClient (TB)
- Refactor: interceptor.session

### Internal
- proxies-6.xsd
  - new Namespace e.g. https://membrane-api.io...6
- '<spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">'
  durch '<apiKeyFileStore .. />' ersetzen (dafür topLevel=true) BT

# Discussion

- Util function to sanitize HTTP Headers in Logs: Authorization, Proxy-Authorization, Cookie, Set-Cookie
  Replace value with *****
  - Use it in MessageTracer


- <api> without port => Change from port 80 to matches all open ports
- ProblemDetails: (TB)
  - When flow = RESPONSE it should always be an internal error!
- For ADRs
  - Response Flow guarantee there is a response
  - Request Flow guarantee there is a request
  - Return guarantee Response is there

- Wenn Exception/Abort passiert sofort Response mit Error setzen.
