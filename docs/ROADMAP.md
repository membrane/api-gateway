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

- Register JSON Schema for YAML at: https://www.schemastore.org/

## (Breaking) Interface Changes

- Removed support for `internal:<name>` syntax in target URLs, leaving `internal://<name>` as the only valid way to call internal APIs.
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
- Groovy:
  - ScriptingUtils: Variable bindings: headers references message.headers with the headers class instead of a map<String,Object>.
    - Difference to SpEL

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
- YAML: JsonSchemaGenerator enable description fields for editor

# 6.4.0

Breaking Changes:
- JSONPath:
  List-to-String conversion now renders full list instead of first element only. Behavior is now different from XPath that returns the first element of a nodelist but it is more consistent with most of JSONPath implementations.

- Migraton Nots:
  - Check JSONPath expressions when returning lists.

- SessionManagerTest: refactor, too slow for Unittest. Move to integration tests. 
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
- Reduce compiler warnings when building the project with maven
      
## Release Notes:

- JSON Schema validation support for JSON Schema 2019-09 and 2020-12 (via networknt json-schema-validator).
  - Document how to select the schema version (e.g., schemaVersion attribute) and the "format" behavior (annotation vs assertion), with a link to usage docs/examples.

# 6.3.0

- Refactor: Beautifier to use the Code from above
- Describe RPM Setup (TP)
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


- Refactor
  - RouterCli:
    - Extract JWT functions
  - SpringConfigurationXSDGeneratingAnnotationProcessor
