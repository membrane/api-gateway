# Membrane Roadmap

# YAML Support

- Rename json schema document:
  - Short name to keep schema ref in YAML instance documents short
  - Ideas:
    - membrane-v1.schema.json
    - membrane-v1.json
    - v1.json
- Name from metadata/name or spec/name?
- Correct YAML example on GitHub README
- Rename in apis.yaml


# 7.0.0

## (Breaking) Interface Changes

- Remove WADLInterceptor
- HttpClient
  - Change Signature: public Exchange call(Exchange exc) throws Exception
    =>  public void call(Exchange exc) throws Exception {
- Remove HttpClientInterceptor.setAdjustHeader(boolean) it is already in HttpClientConfiguration
- Remove xmlSessionIdExtractor if we have a replacement with language 
- Remove HttpUtil.getHTMLErrorBody()
- LogInterceptor:
  - Remove: headerOnly
- ValidatorInterceptor: remove FailureHandler
  - Predominantly used for logging; move logging into validators.
  - Migration: replace FailureHandler usages with validator-level logging; ensure correlation IDs/Exchange context remain available for logs.
  - Check if it is used by customer installations

## Minor
 - Rewrite JSONAssert Tests with RESTAssured

## Discussion

- YAML:
  - apiKey:
    - simple method for specifying a couple of keys in the YAML 
    - SimpleKeyStore: scope feels strange in YAML. Maybe not TextContent for Value


# 6.5.0

- Data Masking
  - Is JSONPath replacement with Jayway possible? <mask>$.cusomter.payment.creditcard
    - Other ways to do it.
- <apiKey/>
    <scriptXX>${json[key]}</scriptXX>
  - See: RateLimitInterceptor
- OpenAPIValidator:
  - <openapi unknownQueryParameters="accept|report|block" .../>
    Default: accept

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
## Release Notes:

- JSON Schema validation support for JSON Schema 2019-09 and 2020-12 (via networknt json-schema-validator).
  - Document how to select the schema version (e.g., schemaVersion attribute) and the "format" behavior (annotation vs assertion), with a link to usage docs/examples.

# 6.3.0

- Convert to UTF-8 source and outputEncoding to UTF-8 (TB)
- TemplateInterceptor Refactoring (TB)
- Template/Static Interceptor: Pretty for text/* (Refactor first) (TB)
  - Pretty on text should trim whitespace incl. linebreaks at start and end
- Refactor: Beautifier to use the Code from above
- Describe RPM Setup (TP)
- examples/routing-traffic/outgoing-api-gateway (TB)
- Cook Book: outgoing-api-gateway (TB) done
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
