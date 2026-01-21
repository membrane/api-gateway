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
- Variable substitution in YAML files
   - e.g. #{}
   - Problem port is an integer
   - JSON Schema support

- Choose:
  - YAML configuration needs makeover.
    - Maybe take out cases?
    - Now
      - choose:
          cases:
            - case: ...
            - case: ...
            - otherwise:


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
- JSONBody
  - Store body as parsed JsonNode or Document
    - If JSON is needed by an interceptor use already parsed JSON