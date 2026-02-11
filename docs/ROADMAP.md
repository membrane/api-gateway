# Membrane Roadmap

# YAML Support

- Correct YAML example on GitHub README

# 7.X

PRIO 1:

- HotReload for YAML
- Support for multiple apis.yaml in one folder
  - idea: load apis.yaml last.
    pattern *.apis.yaml
- Register JSON Schema for YAML at: https://www.schemastore.org TB
- create test asserting that connection reuse via proxy works TP
- Central description of Membrane Languages, Cheat Sheets, links to their docs. TP
- Central description of MEMBRANE_* environment variables
  - Like MEMBRANE_HOME...
  - @coderabbitai look through the code base for usages of these variables and suggest documentation
- Idea: Multiple api.yaml files
  - Membrane reads all *.apis.yaml files in the conf folder or the current working directory
    the apis.yaml is read last and therefore overwrites previous definitions
    e.g. fruitshop.apis.yaml, dlp.apis.yaml, apis.yaml with global and matches all
 



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

## Breaking Changes

- `groovy` interceptor: Return string from script does not set a content type of `text/html` anymore. User has to set the content type manually. 
- headerFilter YAML format has changed.
- Choose Interceptor configuration
- Chain (ChainDef) configuration
- OpenApi: rename `specs` to `openapi`
- **YAML configuration in list elements**:
    * List items can now be written in *inline form* if the list accepts exactly one concrete element type (no polymorphic candidates) and the element is not `collapsed`, not `noEnvelope`, and not string-like.
    * Old wrapper form remains supported: `- <kind>: { ... }` (Only when schema validation is deactivated). 
  
    Old: 
     ```yaml
      properties:
        - property:
            name: driverClassName
            value: org.h2.Driver
        - property:
            name: url
            value: jdbc:h2:./membranedb;AUTO_SERVER=TRUE
  ```
    New:
    ```yaml
      properties:
        - name: driverClassName
          value: org.h2.Driver
        - name: url
          value: jdbc:h2:./membranedb;AUTO_SERVER=TRUE
  ```
- removed `MethodOverrideInterceptor`

## Bug Fixes
- `xml2json`: Ensuring content type alignment and better exception handling.  

## Improvements

- `xml2json`: Better exception handling.  
- Updated documentation and comments for clarity and consistency in related classes (`Header`, `MimeType`, etc.). 

- JSONBody
  - Store body as parsed JsonNode or Document
    - If JSON is needed by an interceptor use already parsed JSON
