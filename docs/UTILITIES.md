# Utility classes in `core`

Check here before writing a new helper or when refactoring. Class-level summaries only — open the class for the exact
signatures. Paths are relative to `core/src/main/java/com/predic8/membrane/core/` unless noted.
Keep this file in sync when adding, renaming or removing a util class.

## Text and strings (`util/text/`)

| Class | Purpose |
|---|---|
| `StringUtil` | Truncate/tail, mask non-printable chars, add line numbers, split by comma, `yes`/`no` flag parsing, last element of a comma list |
| `TextUtil` | Charset lookup, XML pretty-print, glob→regex, English list ("a, b and c"), capitalize, escape quotes, multiline indent handling (`unifyIndent`, `getLines`) |
| `SerializationUtil`, `SerializationFunction` | Pick a serializer by mime type or `Serialization`; path/header value encoding |
| `ToJsonSerializer`, `ToTextSerializer`, `ToURLSerializer`, `ToXMLSerializer` | `Object` → JSON / plain text / URL-encoded / XML string |
| `TerminalColors` | ANSI colors for console output, with global enable/disable |
| `util/StringList` | Parse comma-separated strings into `List`/`Set` |
| `util/TemplateUtil` | Detect template markers (`${...}`) in a string |

## HTTP, URI and URL (`util/`)

| Class | Purpose |
|---|---|
| `HttpUtil` | Line reading, `X-Forwarded-For`, GMT date format, header creation, HTML error responses, status code messages, idempotent-method check, token/whitespace char classes |
| `MediaTypeUtil` | Most specific matching media type from a set |
| `ContentTypeDetector` | Effective content type of a `Message` |
| `http/SSEParser` | Incremental Server-Sent Events parser (`SSEEvent` with `json()`) |
| `URI`, `URIFactory` | Membrane's own URI type and its configurable factory (illegal characters, backslashes); use instead of `java.net.URI` where lenient parsing is needed |
| `URIUtil` | File path ↔ file URI conversion, path-char encoding, normalization |
| `URIValidationUtil` | RFC 3986 character classes and host/port validation |
| `URLUtil` | Authority, path+query, name component, port of a URL |
| `URLParamUtil` | Query/form parameters from an `Exchange`; build, parse and encode query strings |
| `UrlNormalizer` | Normalize a base URL |
| `UriIllegalCharacterDetector` | Detect illegal characters in URIs |
| `WebServerUtil` | Content type by file extension for static serving |
| `ip/IPv6Util` | IPv6 address validation |

## Messages, SOAP, JSON, XML

| Class | Purpose |
|---|---|
| `util/MessageUtil` | Message body as stream/bytes, decompression |
| `util/SOAPUtil` | SOAP detection/analysis (1.1 and 1.2), build SOAP faults, extract fault details and body |
| `util/soap/SoapVersion`, `util/soap/WSDLUtil` | SOAP version constants; rewrite relative WSDL paths |
| `util/wsdl/parser/*` | Lightweight WSDL model (`Definitions`, `Binding`, `Operation`, `Port`, ...) |
| `util/json/JsonUtil` | Parse body to `ObjectNode`, set JSON body, scalar → `JsonNode` |
| `util/json/JsonToXml` | JSON → XML conversion |
| `util/xml/XMLUtil` | XML operations (parsing, namespaces, XPath) |
| `util/xml/XMLEncodingUtil` | XML character encoding |
| `util/xml/NormalizeXMLForJsonUtil` | Normalize XML for JSON conversion |
| `util/MapNamespaceContext` | `NamespaceContext` backed by a map (XPath) |
| `util/LSInputImpl` | `LSInput` implementation for schema resolvers |

## Collections, bytes, errors, concurrency

| Class | Purpose |
|---|---|
| `util/CollectionsUtil` | Concat lists, iterator → list, lowercase set, join |
| `util/Pair` | Simple `record Pair<A, B>` |
| `util/ByteUtil` | Read exact byte counts, drain streams, bit get/set |
| `util/ExceptionUtil` | Root cause, cause matching, message chain, peer-disconnect detection |
| `util/functionalInterfaces/ExceptionThrowingConsumer` | Consumer that may throw |
| `util/Timer`, `TimerManager`, `TimerTaskUtil` | Scheduling periodic/one-shot tasks, `Runnable` → `TimerTask` |
| `util/ConfigurationException` | Config errors (see the `config-error-handling` skill) |
| `util/EndOfStreamException` | Signals an unexpected end of stream |

## Files, OS, network, security, misc

| Class | Purpose |
|---|---|
| `util/FileUtil` | Read/write streams, xml/json file detection, slash handling, resolve, directory part |
| `util/OSUtil` | OS detection, backslash fixing, Windows path detection |
| `util/NetworkUtil` | Free port search, IPv4/IPv6 helpers (brackets, dotted quad, CIDR prefix match) |
| `util/DNSCache` | Cached reverse DNS lookups |
| `util/SecurityUtils` | Password hash helpers (`crypt`-compatible, salt extraction) |
| `util/security/BasicAuthenticationUtil` | Parse/create `Authorization: Basic` headers |
| `util/DateAndTimeUtil` | Pretty-print time spans |
| `util/Util` | Grab bag: line count, socket shutdown, thread pool, split by comma |
| `util/ClassFinder` | Classpath scanning |
| `util/ComparatorFactory` | Comparators for admin-console statistics tables |
| `util/BeanDefinitionBasePathUtil` | Resolve base location of a Spring bean definition |
| `util/MemcachedConnector`, `util/RedisConnector` | Cache backends |
| `util/DLPUtil` | Data-loss-prevention trace warning |

## OpenAPI (`openapi/util/`)

| Class | Purpose |
|---|---|
| `OpenAPIUtil` | API id/version, 3.x vs Swagger 2 detection, path/parameter/property lookup, explode/query checks |
| `SchemaUtil` | `$ref` resolution, effective type, string/array/scalar/object checks, parse scalar by schema |
| `UriUtil` | Trim/ensure slashes, strip query, rewrite URL parts (not the same as `util/URIUtil`) |
| `Utils` | Format validators (UUID, e-mail, date, IP, ISO codes, ...), stream/string conversion, validator request/response builders |
| `UriTemplateMatcher` | Match request paths against OpenAPI URI templates |
| `OpenAPI32Parser` | OpenAPI 3.2 parsing shim |
| `ObjectHolder` | Mutable holder for lambdas |

## Test helpers (`core/src/test/java/com/predic8/membrane/core/`)

| Class | Purpose |
|---|---|
| `util/HttpTestUtil` | Raw HTTP text → `InputStream` for parser tests |
| `util/StringTestUtil` | `InputStream` from string, CRLF normalization |
| `util/ProblemDetailsTestUtil` | Parse a `Response` into `ProblemDetails` |
| `util/RecordingServerTestUtil` | Free port, throw-away server that records whether it was called |
| `openapi/util/JsonTestUtil` | Build `JsonNode`/bytes from maps in tests |
| `openapi/util/OpenAPITestUtils` | Load/parse OpenAPI resources, create proxies from specs |
