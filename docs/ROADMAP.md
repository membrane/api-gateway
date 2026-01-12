# Membrane Roadmap

# YAML Support

- Correct YAML example on GitHub README

# 7.X

- Question: Should we remove the old rest2soap interceptor(using XSLT) in favor of the new template based examples?
- Do we need add(Rule,Source) and getRuleBySource(Manual|Spring)?
- Rewrite ACL to use the YAML configuration instead of external XML files
- Fix maven central publish job
- JMXExporter:
  - Tutorial
  - Documentation
  - See JmxExporter
- remove basic-xml-interceptor example?
- logs:
  - Instead of:
    18:37:33,693  INFO 1 main HttpEndpointListener:92 {} - listening at '*:2000'
    18:37:33,693  INFO 1 main HttpEndpointListener:92 {} - listening at '*:2001'
    => listening at *:2000, *:2001
- refactor JdbcUserDataProvider
- Refine YAML for balancer: clustersFromSpring
- wsdlRewriter YAML is not working
- use @MCElement(collapsed=true) for suitable classes


# 7.0.4

- Discuss renaming the WebSocketInterceptor.flow to something else to avoid confusion with flowParser
- YAML parsing:
  - When the reason for a parse error is clear. Shorten error message.
- BalancerHealthMonitor:
  - @PostConstruct instead of InitializingBean, DisposableBean
- Scripting: expose beanRegistry
- Migrate deprecated finally to try with ressources
- if: Add hint in documentation: use choice otherwise for else
- accessControl:
     - Warning: Gets complicated!
     - Migrate to simple yaml config
     - Restrict on ips, hostname not paths
     - ipv6, wildcards

# 7.1.0

- Register JSON Schema for YAML at: https://www.schemastore.org
- Grafana Dashboard: Complete Dashboard for Membrane with documentation in examples/monitoring/grafana
- Remove GroovyTemplateInterceptor (Not Template Interceptor)
  - Old an unused
- create test asserting that connection reuse via proxy works
- Configuration independent lookup of beans. I just want bean foo and I do not care where it is defined.
  - See: ChainInterceptor.getBean(String)
  - Maybe a BeanRegistry implementation for Spring?

# 7.0.4

- Discuss renaming the WebSocketInterceptor.flow to something else to avoid confusion with flowParser
- do not pass a `Router` reference into all sorts of beans: Access to global functionality should happen only on a very limited basis.


# 7.0.1

- Central description of Membrane Languages, Cheat Sheets, links to their docs.
- Central desciption of MEMBRANE_* environment variables
  - Like MEMBRANE_HOME...
  - @coderabbitai look through the code base for usages of these variables and suggest documentation
- Restore Kubernetes Startup Thread
- Fix `YAMLParsingTest.errorInListItemUniqueness()`
- Check 404 in AdminConsole => Client Requests
  - API to get client requests returns 404, if called without admin console access 
 

## (Breaking) Interface Changes
- JMX: Name changes to "io.membrane-api:00=routers, name="
- Removed GateKeeperClientInterceptor
- Removed support for `internal:<name>` syntax in target URLs, leaving `internal://<name>` as the only valid way to call internal APIs.
- Remove WADLInterceptor
- HttpClient TB (done)
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
    - SpEL headers.foo should return comma separated list of all values.
- Delete unneeded proxies.xml in examples CG
- for distribution: README with Link to Github for XML-based example configurations TB
- update schema reference to 7.0.0, integrate into ConsistentVersionNumbers.java (done)
- improve error output on
  - schema validation error
  - bean setter exception
  - port occupied (done)

- header['x-unknown'] returns null instead of empty string !!!!!!!!!!!!
- SpEL: header is now of class HeaderMap insteadof SpelHeader

## Breaking Changes

- YAML Configuration as default
- Use of colors in logs
- Removed camelCase conversion of header access in Groovy scripts instead of header.contentType use header['Content-Type']
- JMX namespace changed from org.membrane-soa to io.membrane-api.

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
