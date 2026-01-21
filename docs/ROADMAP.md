# Membrane Roadmap

# YAML Support

- Correct YAML example on GitHub README

# 7.X

PRIO 1:

- Tutorials:
  - Add how to run the tutorials in a Docker container
- HotReload for YAML
- YAML parsing:
  - Shorten YAML error messages
  - When the reason for a parse error is clear. Shorten error message.
- if: Add hint in documentation: use choice otherwise for else TB
- Register JSON Schema for YAML at: https://www.schemastore.org TB
- create test asserting that connection reuse via proxy works TP
- Central description of Membrane Languages, Cheat Sheets, links to their docs.
- Central description of MEMBRANE_* environment variables
  - Like MEMBRANE_HOME...
  - @coderabbitai look through the code base for usages of these variables and suggest documentation


PRIO 2:
- Fix maven central publish job
- Tutorial: Replace httpbin and catfact TB
- use @MCElement(collapsed=true) for suitable classes
- if:
    test: param.case == 'c'
    flow:
      - return:
          status: 500
    else:
      - 

PRIO 3:

- JMXExporter:
  - Tutorial
  - Example
  - See JmxExporter
- upgrade to jackson 3
  - When OpenAPI Parser: swagger-parser-v3 is released with Jackson 3 support
- refactor JdbcUserDataProvider
- Refine YAML for balancer: clustersFromSpring
- wsdlRewriter YAML is not working
- Discuss renaming the WebSocketInterceptor.flow to something else to avoid confusion with flowParser
- Migrate deprecated finally to try with resources
- YAML:
  - method: Suggest GET, POST, ...
  - Language is case sensitive: e.g. language: SPEL is not valid according to the Schema and produces: TB
       Invalid YAML: does not have a value in the enumeration ["groovy", "spel", "xpath", "jsonpath"]
  - openapi/rewrite/protocol provide http and https options
  
  
# 7.0.4

- Discuss renaming the WebSocketInterceptor.flow to something else to avoid confusion with flowParser
- YAML parsing:
  - When the reason for a parse error is clear. Shorten error message.
- BalancerHealthMonitor:
  - @PostConstruct instead of InitializingBean, DisposableBean
- Migrate deprecated finally to try with resources
- if: Add hint in documentation: use choice otherwise for else
- accessControl:
     - Warning: Gets complicated!
     - Migrate to simple yaml config
     - Restrict on ips, hostname not paths
     - ipv6, wildcards
- Register JSON Schema for YAML at: https://www.schemastore.org
- Grafana Dashboard: Complete Dashboard for Membrane with documentation in examples/monitoring/grafana
- Remove GroovyTemplateInterceptor (Not Template Interceptor)
  - Old and unused
- create test asserting that connection reuse via proxy works
- Configuration independent lookup of beans. I just want bean foo and I do not care where it is defined.
  - See: ChainInterceptor.getBean(String)
  - Maybe a BeanRegistry implementation for Spring?

## Discussion

- Discuss renaming the WebSocketInterceptor.flow to something else to avoid confusion with flowParser
- do not pass a `Router` reference into all sorts of beans: Access to global functionality should happen only on a very limited basis.
  - Before start discuss with team

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
