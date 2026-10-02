# Membrane Roadmap



PRIO 1:
- Add performance tutorial or tuning guide
  - forgetful exchange store
- set backlog to 1000
  - provide production tutorial with backlog = 100
  - production = true
  - not hotreload
- When released:
  - Add SqlProtection to README.md
  - Udate OAuth2 sections in README.md
- Proxy Server Configuration Sample 
  - explains how to configure a proxy server
- Register JSON Schema for YAML at: https://www.schemastore.org TB
- create test asserting that connection reuse via proxy works TP
- Central description of Membrane Languages, Cheat Sheets, links to their docs. TP
- Central description of MEMBRANE_* environment variables
  - Like MEMBRANE_HOME...
  - @coderabbitai look through the code base for usages of these variables and suggest documentation 

PRIO 2:
- Remove MemoryExchangeStore
  - It was used only by Membrane Monitor
- Tutorial: Replace httpbin and catfact TB

PRIO 3:
- OpenAPI: validate `in: cookie` parameters
  - Path, query and header parameters are validated; cookie parameters currently are not.
- ReplaceInterceptor: Support Languages, tutorial/transformation
- WebServiceExplorerInterceptor
  - Support multiple services, ports, ...
  - Refactor
- JMXExporter:
  - Tutorial
  - Example
  - See JmxExporter
- upgrade to jackson 3
  - When OpenAPI Parser: swagger-parser-v3 is released with Jackson 3 support
- refactor JdbcUserDataProvider
- Discuss renaming the WebSocketInterceptor.flow to something else to avoid confusion with flowParser
- Migrate deprecated finally to try with resources
- YAML:
  - method: Suggest GET, POST, ...
  - openapi/rewrite/protocol provide http and https options
- Refactor: File-, JDBC-, LDAP- and StaticUserProvider
- Make the maximum HTTP line length configurable in YAML
  - Story: the limit for a single start line or header line (default 8092) can only be set with the JVM system property `membrane.core.http.body.maxlinelength`, e.g. through `JAVA_OPTS`. It is read once into static fields of `HttpUtil` and `Http2Logic` (HTTP/2 header decoder), so it is global, undocumented, and needs a restart to change. The name is misleading: it limits start and header lines, not the body.
  - Move it into the configuration, e.g. the transport configuration from #3315, and pass it down to `HttpUtil.readLine(InputStream, int)`, which takes the limit as a parameter once #3379 is merged.
  - Keep the system property as a fallback, or deprecate it.
  - Document that zero or less means no limit.
  - Related: #3382 (a line over the limit gets a connection reset instead of 414/431), #3378 item 3 (8092 is probably a typo for 8192).

## Membrane 8.0.0 (Java 25)

- Remove XML configuration
- configure log4j with YAML
- Remove SOAP2REST XSLT Interceptor:
  - rm HTTP2XMLInterceptor
  - rm com.predic8.membrane.core.http.xml
- Log harmonization:
  - Decide if log message start with uppercase or lowercase.
     - e.g.:  Error loading log configuration., Started 1 API:, Closing server port:, listening at '*:2000'
- Remove proxies.inactive.xml from conf Folder
  - Also README.md in conf
- Upgrade baseline to Java 25
  - Bump `javac.source`/`javac.target` from 21 to 25 in the root pom.xml
  - Simplify `Util.createNewThreadPool()`: the reflective lookup of `Executors.newVirtualThreadPerTaskExecutor` and the `--enable-preview` handling are leftovers from the Java 19/20 preview era; call it directly and drop the `-Dmembrane.virtualthreads` fallback (or keep the flag as a plain if).
- Evaluate: virtual threads for HTTP/1 client connections
  - Story: HTTP/2 (`Http2ServerHandler`, `Http2Client`) already runs on virtual threads via `Util.createNewThreadPool()`. HTTP/1 connections still go through the `ThreadPoolExecutor` in `HttpTransport` (platform threads, core 20, unbounded max). On Java 21 this was left alone deliberately: a virtual thread blocking inside a `synchronized` block pins its carrier thread, which is risky for connection handling under load. JEP 491 (Java 24) removed monitor pinning, so with a Java 25 baseline that argument is gone.
  - Check whether switching `HttpTransport` to a virtual-thread-per-task executor makes sense. If yes, account for what the pool currently provides:
    - Backpressure: `maxThreadPoolSize` is a documented attribute and `HttpEndpointListener` handles `RejectedExecutionException` by closing the socket. A per-task executor never rejects — replace with a `Semaphore` or rely on `concurrentConnectionLimitPerIp`. Dropping the attribute is a breaking change.
    - Thread naming: keep "router" thread names via `Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("router-", 0).factory())`.
    - Graceful shutdown / hot deploy: `shutdown()` + `awaitTermination()` in `closeAll()` works unchanged with a per-task executor.
- Read HTTP start lines and header lines in bulk (`HttpUtil.readLine`)
  - Story: `readLine` calls `read()` once per byte, and every call takes the stream's lock. Reading ahead into an array and scanning it took 23–28 instead of 93–294 ns per line in a JDK 25 micro-benchmark, and saved about 1.8 µs of gateway user CPU per request in the Azure fullproxy test; throughput stayed within noise (measured on an earlier variant of PR #3379).
  - Parked for 8.0: so it never blocks where the byte-by-byte loop would not, the bulk read has to know how many bytes the `BufferedInputStream` holds, which needs a subclass. On Java 21–23 a subclass locks with `synchronized`, so an HTTP/2 virtual thread that reads a backend response pins its carrier thread; with as many slow backend reads as CPU cores, every virtual thread stalls. JEP 491 (Java 24) removed monitor pinning.
  - Do it after the line-parsing fixes in #3378, so the bulk path follows the corrected terminator rules instead of mirroring each fix.
  - Design: let the connection stream scan its own buffer for the terminator instead of mark/reset/skip. That also avoids leaving a mark pending after the headers, which makes the first body reads refill in small pieces.
  - Starting point: branch `perf/http-util-readline-bulk-read` (implementation, comparison tests across stream types, packet/stall sweep with a misreported `available()`).
- `RuleManager.addProxy`/`addProxyAndOpenPortIfNew`: drop the unused `RuleDefinitionSource source` parameter
  - Story: `source` (`SPRING` vs `MANUAL`) is threaded through both methods and ~10 call sites across `main` and `test` but never read anywhere in `RuleManager` — no field stores it, no branch or log line depends on it. Either wire it into an actual behavior/log distinction, or remove the parameter and the `RuleDefinitionSource` enum from all callers in one cleanup pass.
- `xmlProtection` and `jsonProtection`: inspect responses, not only requests
  - Story: both plugins pin themselves to the request flow in their constructors (`setAppliedFlow(REQUEST_FLOW)`), so a backend answering with a malicious or oversized document is never inspected. A gateway that shields the backend from the client should be able to shield the client from the backend as well.
  - This matters most for an outgoing proxy: there the party to protect is the internal client, and the untrusted document arrives in the *response* from some server on the internet.
  - **Breaking**: letting the flow be chosen means the plugin has to be placed explicitly in a `request:` or `response:` block instead of being written directly into `flow:`, where it silently means "request only" today.

    Old:
    ```yaml
    flow:
      - xmlProtection: {}
    ```
    New:
    ```yaml
    flow:
      - request:
          - xmlProtection: {}
      - response:
          - xmlProtection: {}
    ```
  - To decide: whether a violation found in a response maps to a gateway error (502) rather than the 400 a request violation gets, since the fault is the backend's and the detail must not leak to the client.
- Scripting `cookie`/`cookies` map (`LazyCookieMap`) read-only
  - for Groovy
  - Story: `put`/`remove`/`clear`/`putAll` (and `keySet`/`entrySet`/`values` views) change a private copy and are silently dropped. The parsed map is also cached, so it goes stale after `header.put('Cookie', …)`.
  - Fix: mutators throw `UnsupportedOperationException` pointing to `header.put('Cookie', …)`; re-parse on every access (live like `SpELCookie`).
  - **Breaking**: scripts calling `cookie.put(...)` fail instead of silently doing nothing.
  - Related: in the response flow `cookie` is always empty (it only parses `Cookie`, not `Set-Cookie`). Document or address separately.
  - What about setting cookies?
- Reorganisation of *Util* classes. Check for duplicates and right place.

## Breaking Changes

- `groovy` interceptor: Return string from script does not set a content type of `text/html` anymore. User has to set the content type manually. 
- headerFilter YAML format has changed.
- Choose Interceptor configuration
- Chain (ChainDef) configuration
- BasicAuthentication interceptor removes the Authentication header from the request. 
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

  