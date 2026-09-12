# Membrane API Gateway Tutorials

Learn how to configure APIs with Membrane through a set of simple, hands-on tutorials.


## [Getting Started](getting-started)

The 'Getting Started' tutorial takes about 15 minutes. After finishing it, you will be able to set up the API Gateway and handle common API Gateway use cases.

Complete this tutorial before moving on to the JSON or XML tutorials.


## [JSON](json)

This tutorial builds on 'Getting Started'. It explains how to read, create and transform JSON data, how to use JsonPath to extract information or compute values.


## [XML](xml)

If your APIs use XML as input or output, this tutorial provides useful configurations and patterns for working with XML.


## [Advanced](advanced)

Learn how to use Membrane in more advanced scenarios. Topics include path rewriting, scripting, conditions and more.


## [Operation](operation)

Run and observe Membrane in production. 


## [Server-Sent Events](sse)

Proxy a Server-Sent Events (SSE) stream and watch it in a browser-based debugger.


## [AI / MCP](mcp)

Expose Membrane as an MCP server for AI clients, inspect recent API traffic, restrict MCP tools, and protect the endpoint with an API key.


## [LLM Gateway](llm-gateway)

Put Membrane in front of an LLM provider's chat API (OpenAI, Claude, or Gemini). Share one
provider key among many clients, enforce token and model policies, and issue per-user API keys
with their own token quotas.


## [SSL/TLS](ssl-tls)

Terminate TLS with your own certificate, share one TLS configuration between several APIs, or
forward encrypted connections to the backend untouched and route them by their TLS server name.


## [Security](security)

Protect an API against common threats. Covers Basic Authentication, access control lists, and
JSON/XML/GraphQL/SQL-injection protection.


## [JWT](jwt)

Issue signed JSON Web Tokens and protect an API by validating them. Covers Bearer tokens and signature/expiry/audience checks.


## [API Keys](api-keys)

Secure an API with API keys. Covers simple key validation, scope-based access control, deriving
key requirements from an OpenAPI document, and storing keys in PostgreSQL or MongoDB.


## [OAuth2](oauth2)

Run a complete OAuth2 setup with Membrane acting as both the authorization server and the
token-validating gateway. Covers client credentials, password flow, automatic token renewal,
and a distributed issuer/validator setup.


## [SOAP Web Services (Legacy)](soap)

If you need to integrate legacy SOAP Web Services, this tutorial provides examples and practical guidance.


## [WSDL to OpenAPI](wsdl-to-openapi)

Expose a legacy SOAP/WSDL web service as a REST/OpenAPI API. Covers automatic and manual
per-operation conversion, how XSD constructs map to OpenAPI, and how SOAP faults become problem
details documents.


## [SOAP/REST Converter](soap-rest-converter)

Transform requests and responses between REST/JSON and SOAP/XML by hand, with templates and
Groovy scripts. Covers converting REST calls to SOAP and back, JSON/SOAP array conversion in
both directions, and SOAP fault handling.


## [Web Services Security](web-services-security)

Secure SOAP messages with WS-Security: add and validate a `wsse:UsernameToken`, a `wsu:Timestamp`, and a `ds:Signature`, and encrypt the body with XML Encryption.


## [OpenAPI](openapi)

Validate requests against an OpenAPI description and learn the features added in OpenAPI 3.2, such as the QUERY method.


## [OpenAPI Rewriting](openapi/rewrite)

Understand how Membrane rewrites the server URLs of an OpenAPI document served at `/api-docs`, and how to pin the host, port, base path or protocol - for example when running behind a reverse proxy.

---

## Start Membrane with Docker

To run the tutorials via Docker, use the `run-docker.sh` (Linux/macOS) or `run-docker.cmd` (Windows) script instead of `membrane.sh`.

**Important:** Always run the script from the directory that contains the tutorial yaml.

## Questions and Feedback

If you have questions, feedback or run into any issues, we’re happy to help.
You can also join the Membrane discussions on GitHub:

https://github.com/membrane/api-gateway/discussions
