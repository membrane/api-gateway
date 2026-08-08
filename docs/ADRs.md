# Architecture Decision Log

## ADR-008 Character Encoding of Inbound XML

Status: FOR DISCUSSION
Date: 2026-08-08

### Context

Deciding which charset an inbound XML body is in has three answers in Membrane today, depending on
which code path reaches the body first:

| Path | What decides the charset | Ignores |
|---|---|---|
| `XmlDomBody.documentOf` — hands the parser a byte stream | BOM, then the `encoding=` declaration, else UTF-8 | the `Content-Type` `charset` |
| `XMLUtil.getInputSource` — wraps the body in `new InputStreamReader(...)` with no charset | `Charset.defaultCharset()` | both the header **and** the declaration |
| `Message.getBodyAsStringDecoded` | the `Content-Type` `charset`, else UTF-8 (`TextUtil.getCharset`) | the declaration |

RFC 7303 §3.1/§8.5 is unambiguous for `application/xml` and `text/xml`: when the `charset` parameter
is present it is authoritative and overrides the internal declaration. None of the three paths
implements that.

The second row is a defect on its own terms, independent of RFC 7303. Handing a parser a `Reader`
means the XML declaration can no longer be honoured — the XML specification says an encoding
declaration is ignored on a character stream, because by then the decoding has already happened. So
`XPathExchangeExpression` and everything else built on `getInputSource` decodes by JVM default
charset. Since JEP 400 that is UTF-8 unless `-Dfile.encoding` says otherwise, which is why this has
not been noticed; a body that correctly declares `ISO-8859-1` is nonetheless misdecoded today, and the
behaviour is platform- and flag-dependent, which no decoding rule should be.

Membrane's *outbound* side already assumes RFC 7303 precedence. `XmlDomBody.correctContentTypeCharset`
rewrites the `charset` parameter to match the bytes it just wrote, precisely because a parameter that
says something else makes a receiver misdecode — and makes a receiving WS-Security stack digest bytes
other than the ones that were signed. Inbound and outbound therefore disagree with each other.

For SOAP specifically, header precedence is also what the stacks on the other end of the wire do:
CXF takes the encoding from the `Content-Type`.

This is an interoperability and correctness problem, not a vulnerability. A misdecode changes the
characters that canonicalization sees, so a signature over the affected content fails to verify —
loudly, rather than being accepted as something it is not.

### Decision

- **RFC 7303 precedence is the rule for inbound XML:** the `Content-Type` `charset` when present,
  otherwise the BOM or `encoding=` declaration, otherwise UTF-8. One rule, all XML entry points.
- **It lives in one place.** The resolution belongs in a single helper that every XML entry point uses
  (`XMLUtil.getInputSource` and `XmlDomBody` being the two that matter), rather than each parse site
  deciding for itself. The three-way split above is what happens otherwise.
- **No `Reader` without an explicit charset, ever.** Where the declaration is to be honoured, the
  parser gets bytes; where the header wins, the charset is passed explicitly. `new
  InputStreamReader(stream)` in XML handling is a defect, not a shortcut.
- **No per-API override is added.** There is no evidence yet that anyone needs to opt out, and a
  configuration switch on message decoding would be a lasting cost; revisit if a real deployment
  produces one.

### Consequences

- A body whose header and declaration disagree changes behaviour. That is the point of the change, but
  it is a behaviour change on a path with no obvious symptom, so it belongs in a minor release with a
  release note rather than a patch.
- Membrane now trusts the sender's `charset` over the sender's declaration. Where an intermediary
  stamps a blanket `charset=utf-8` onto traffic that is really ISO-8859-1 with a correct declaration,
  this makes Membrane misdecode a body that previously parsed. RFC 7303 says the header wins, and CXF
  agrees, so conforming is the right default — but the failure mode moves, it does not vanish.
- Bodies that declare an encoding other than UTF-8 and carry no `charset` parameter start decoding
  correctly on the two `getInputSource` callers, where they are currently decoded by JVM default:
  `XPathExchangeExpression` (every `xpath` in a configuration) and `CommonBuiltInFunctions`. The
  `xpath` references inside `wsSecurity` are not among them — those evaluate against the `Document`
  `XmlDomBody` already parsed, so they follow the byte path and change only through the
  header-precedence half of this decision.
- `XmlDomBody`'s outbound correction stays as it is: it is the mirror of this decision, and the two now
  agree.
- Applies to XML only. JSON has its own rules (RFC 8259: UTF-8, no `charset` parameter) and is not in
  scope.

## ADR-007 No XML Nesting-Depth Limit in XML Processing

Status: PROPOSED
Date: 2026-08-07

### Context

A deeply nested XML document is a resource-exhaustion vector against any *recursive* consumer of the
parsed tree. Membrane has several: the identity `Transformer` that re-serializes a DOM
(`XmlDomBody.serialize`), the JSR-105 XML Signature implementation behind `wsSecurity`, XSLT, and
until recently a recursive DOM walk in `WsSecurityXmlUtil`. The failure mode is worse than a rejected
message: nesting overflows the JVM stack, and `StackOverflowError` is an `Error`, so it escapes the
`catch (Exception ...)` blocks that turn a bad message into a clean error response.

Nothing in Membrane caps depth today. `HardenedXmlParser` does not set `jdk.xml.maxElementDepth`, and
`FEATURE_SECURE_PROCESSING` does not set it either — the JAXP default is `0`, i.e. unlimited — so a
hostile document reaches a DOM unimpeded. The obvious reflex is to set that property, either on the
hardened parser or as a JVM-wide system property next to the entity limits already in
`start_router.sh`.

That reflex is wrong for a gateway. Membrane does not own the XML it forwards. A depth that is
absurd for one API is ordinary for another: industry schemas nest far deeper than hand-written
payloads, and the limit that makes an attack impossible is well above any value that would catch one
cheaply. A global limit therefore buys little and silently breaks legitimate traffic, on a code path
where the only symptom is a rejected message with no obvious cause.

### Decision

- **No nesting-depth limit is applied in XML processing, and none is set globally.** Neither
  `HardenedXmlParser` nor any launcher script sets `jdk.xml.maxElementDepth`. XML handling stays
  agnostic about how deep the documents it forwards are.
- Code that walks a parsed document is responsible for not recursing over attacker-controlled depth.
  `WsSecurityXmlUtil.forEachDescendantElement` is iterative for this reason, and new traversals
  should be too. This is the part Membrane can guarantee without knowing anything about the payload.
- A user who needs a depth limit configures one, per API, where the cost is theirs to judge:
  - `xmlProtection` with `maxDepth` — the natural place, since it already screens XML in a streaming
    StAX loop and so rejects before any DOM is built. The attribute does not exist yet; see
    [#3127](https://github.com/membrane/api-gateway/issues/3127).
  - `limit` with `maxBodyLength`, which bounds depth indirectly: nesting costs bytes, so a body cap
    caps depth. Coarser, but available today and useful whatever the payload format.

### Consequences

- Membrane forwards arbitrarily deep XML by default. That is deliberate: the gateway does not decide
  what is too deep for a backend.
- Until `xmlProtection` gains `maxDepth`, `limit` is the only configurable defense, and it is
  indirect.
- Recursion over a parsed document becomes a review item for XML-handling code, since the parser
  offers no backstop. The comment on `WsSecurityXmlUtil.forEachDescendantElement` records the reason
  at the one place it currently matters.
- The `jdk.xml.*` entity options in `start_router.sh` are a separate mechanism and are untouched by
  this decision. Note they do the opposite of capping: both are set to `0`, which in JAXP means *no
  limit*, so `jdk.xml.totalEntitySizeLimit=0` removes the JDK's default cap and
  `jdk.xml.maxGeneralEntitySizeLimit=0` is a no-op. They also only apply when the shell launcher
  starts the process, so a WAR or embedded deployment does not get them — see
  [#3128](https://github.com/membrane/api-gateway/issues/3128).

## ADR-006 WS-Security Configuration Grammar

Status: ACCEPTED
Date: 2026-08-07

### Context

WS-Security support started as separate flat interceptors: `wsuTimestamp`, `usernameToken`,
`usernameTokenVerifier`, `digitalSignature`, `digitalSignatureVerifier`. XML Encryption and
Decryption are still to come. Flat siblings duplicate the keystore/truststore/namespace
configuration on every element, cannot validate constraints that span elements, and leave the
lifecycle of the `wsse:Security` header unowned.

Membrane is a gateway, so the common case is not "verify" or "sign" but both on the same message:
validate what the client sent, then re-secure for the backend, and the mirror image on the way
back.

### Decision

- One `wsSecurity` element, usable in both the request and the response flow. Direction is not
  part of its grammar; it comes from the existing `request:` / `response:` flow containers.
- It holds two optional, ordered child lists: `validate` (consume inbound security) and `secure`
  (apply outbound security). The list order is the processing order.
- Order is not hardcoded. WS-SecurityPolicy sanctions both `sp:SignBeforeEncrypting` and
  `sp:EncryptBeforeSigning`, and a receiver must mirror whatever the sender did, so a fixed
  sign-then-encrypt order would make legitimate peers unreachable.
- The element fixes only the group boundary: `validate` runs before `secure`. That boundary is
  where the `wsse:Security` header targeted at this element's actor/role is consumed and removed,
  before `secure` creates a fresh one. Headers targeted at other actors pass through untouched.
- `init()` enforces the constraints that are decidable at configuration time, in place of a fixed
  order — most importantly, in `secure` a part referenced by a later part must appear earlier
  (a `signature` with `by: TIMESTAMP` listed before its `timestamp` is a configuration error,
  not a silently under-covered message).
- The parent owns `actor`/role, `mustUnderstand`, and the shared keyStore/trustStore.
- Splitting into several `wsSecurity` elements stays legal, and is how a body transformation is
  interleaved between validating and re-securing.
- The internal processor SPI is defined over a shared `org.w3c.dom.Document`. JSR-105 types
  (`XMLSignatureFactory`, `KeyInfoFactory`, `Reference`) must not appear on `@MCElement` config
  classes or in the SPI, so the encryption implementation (hand-rolled vs. WSS4J) stays an open
  choice that does not affect the grammar.

### Accepted algorithms are stricter than produced ones

`validate/signature` fixes its accepted algorithms instead of taking whatever the peer chose:
SHA-256 or better for both digest and signature, and canonicalization as the only permitted
`ds:Transform`. `secure/signature` stays configurable and still offers `rsa-sha1`, because a legacy
backend may require it.

The asymmetry is the point. Accepting what we can emit would let any peer downgrade a message to
SHA-1 whatever this gateway signs with. The transform allowlist is not a capability limit but a
correctness one: `Reference.getURI()` says which element a reference names, while the transform chain
decides what was actually digested, so an XPath transform can satisfy a `requiredReferences` check
against `#body-id` while covering none of the body. The JDK's secure validation mode is also enabled
explicitly rather than relied on by default — it is what refuses `file`/`http` reference URIs.

### An unvalidated header is not forwarded

The header targeted at this element's actor is consumed at the group boundary even when there is no
`validate` list. Without that, a `secure`-only element appends its own tokens to the header the peer
sent, and the backend receives the client's `UsernameToken` and signature as if the gateway had
vouched for them.

The `wsu:Timestamp` is the one child that survives: it asserts nothing on its own, and
`secure/signature` may reference it (`by: TIMESTAMP`) to cover a freshness window the message already
carried. Everything else is dropped — an allowlist, since every other child of a `wsse:Security`
header is a claim.

### Deviation from ADR-001 (ProblemDetails)

WS-Security failures return a `soap:Fault`, not ProblemDetails. SOAP clients cannot consume
RFC 7807, and the WS-Security specification defines the fault codes to use:

- missing or malformed security header: `wsse:InvalidSecurity`
- a refused algorithm or transform: `wsse:UnsupportedAlgorithm`
- a token kind or `Password` type not processed here: `wsse:UnsupportedSecurityToken`
- failed authentication, bad password or digest, replayed nonce: `wsse:FailedAuthentication`
- signature verification failure, required reference not covered: `wsse:FailedCheck`
- unresolvable or malformed token reference: `wsse:SecurityTokenUnavailable`,
  `wsse:InvalidSecurityToken`

The fault matches the envelope version of the offending message (SOAP 1.1 `faultcode` vs.
SOAP 1.2 `Code`/`Subcode`), and honours the existing production-mode detail suppression.

One exception stays on ProblemDetails: a request body that is not SOAP at all, where no fault
envelope can be produced.

### Consequences

- `timestamp` exists in both lists, like `signature` does: under `secure` it creates a freshness window
  and takes a `ttl`, under `validate` it enforces one and takes a `clockSkew`. The `validate` one is
  what makes a window enforceable without a signature — useful when the channel is authenticated some
  other way — while `signature` with a `TIMESTAMP` required reference is what makes the window
  trustworthy. `SignatureReference.By.USERNAME_TOKEN` exists for the same reason on the credential side:
  the signed-UsernameToken policy is not expressible without it.
- The five flat elements are renamed into `validate` / `secure` list parts. They are unreleased,
  with no tutorials or integration tests, so no deprecation cycle is needed — provided no release
  ships them as public configuration first.
- Message bodies gain a DOM-holding representation (`XmlDomBody`) so the parts share one parsed
  `Document` instead of each interceptor re-parsing and re-serializing. This also removes the
  duplicated `wsu:Id` re-marking, since marking an ID mutates the `Document` itself.
- `writeBack` must stop hardcoding `exc.getRequest()` and act on the message for the current flow;
  SpEL evaluation must follow the actual flow instead of pinning `Flow.REQUEST`.

## ADR-005 Log Levels

Status: PROPOSED
Date: 2026-05-18

## Context

Log events should be logged with an appropriate level.

## Idea

FATAL:
- Meaning: The application cannot continue running. Immediate shutdown or unusable state.

ERROR:
- Meaning: A serious problem occurred, but the server can still run.
- Examples:
  - Unhandled exception during a request
  - Invalid configuration
  - Database cannot be reached

WARN:
- Meaning: Something unexpected or undesirable happened, but processing can continue.
- Examples:
  - Backend service is not available
  - TLS errors with backend service
  - Scripting Error in Groovy, Templates, SpEL

INFO:
- Meaning: Important normal operational events.
- Examples:
  - Startup
  - Shutdown
  - JWT token validation failed
  - Password validation failed
  - JSON, OpenAPI, XML validation failed
  - Connection errors between client and Membrane, e.g. Socket closed
  - Wrong Content-Type for json2xml transformation

DEBUG:
- Meaning: Detailed technical information for troubleshooting.
- Examples:
  - HTTP request and response headers
  - Processing steps

TRACE:
- Meaning: Extremely detailed internal execution flow.
- Examples:
  - Complete dumps of HTTP messages
  - Bytes sent and received
  - Can contain sensitive information (do not use in production!)

Do not Log:
- 404 Not Found



## ADR-004 Logging

Status: PROPOSED
Date: 2026-01-29

## Context

## Idea

- Log at level INFO interesting events like:
  - OpenAPI validation failed
  - ACL failed
  - ...
- Make it easier to explore Membrane and make it work
- Membrane ships with INFO level for development
- Set root level to WARN for production

## ADR-003 Access to beans with Router 

Status: ACCEPTED
Date: 2026-01-29

### Context

- Interceptors and other components need access to infrastructure singletons and other beans.
  - Examples:
    - ExchangeStore
    - URIFactory
    - The Proxy the interceptor belongs to
    - ...
- Currently the router is passed to the interceptors as a parameter.
- Interceptors like request, response, for and manage a list of other interceptors.
- The ProxyAware interface signals that an interceptor is aware of the proxy it belongs to.
  - Somebody has to call setProxy on the interceptor.

### Decision

- We do not use DI to inject beans into interceptors.
- Interceptors get a reference to the router via init(Router router) and ask the router for beans.

## ADR-002 Flow Guarantees

Status: PROPOSED
Date: 2026-01-20

### Decision

- Guarantees for flows:
  - that in a Response-flow there is a response 
  - that in a Request-flow there is a request  
  - No null checks needed in those cases


## ADR-001 ProblemDetails

Status: ACCEPTED

### Decision

- Use ProblemDetails to return error messages to the API caller
- Fields:
  - type, title
  - subtype(s)
    - subtype should be enough to identify error
    - first subtype component must be uncritical for security
      e.g. /security/oauth2/keystore/invalid-alias
  - component
    - Component that caused the error
    - Mandatory
  - detail:
    - For humans easily readable description
    - Used because a log of examples use this field
    - Is also returned in production
  - internal/message:
    - Message from an exception
- Returning a PD is no substitute for logging

### Consequences:

# ADR Discussions

## Make flow a property of exchange 
  - That would support a method like getMessage
  - Flow is more a property of E

## Type of setProperty

- Should it always be String or should it be possible to set other objects?

Agreed!

### Considerations
- Object makes sense to cover more complex use cases
- String makes it easier

### Decision

## proxies.xml

- Take out ASF License

## Logging of data

Should we log data in case of error?

## Exception for Returning Detailed Error Messages

e.g. setHeader should return in not production mode details about the error. But setHeader should not deal with deciding to use XML or JSON for the response.

# Naming

- ..ExampleTest

## More

- WADL remove?
- When error case is known do not log stacktraces

## Internal Proxy?

- <api name=""/> opens port 80 / Do we need internal 

# setHeader returns null

 <setHeader value="headers['unknown'] ...> should set null.

 - Makes handling less easy if it is null


# Other

- Use conventional commits

# Use double brace initialization?

{{}}

See: https://stackoverflow.com/questions/1958636/what-is-double-brace-initialization-in-java

# Initialization

Init first proxies and than their interceptors

Should implementations of init() in subclasses of Interceptor call init() on super? 
- Otherwise so initialization is missing out.
# Internal Property Names

- Should start with "membrane."
- All lowercase
- See: SecuritySchemes

# Keep K8S

# java.net.URI or com.predic8.membrane.core.util.URI ?
- our URI is a 1:1 replacement for the java.net.URI.
- by default, our URI delegates to the java URI
- but: If URIFacteroy is configured this way, our allows special characters. This is required for proxying some 
  Microsoft products.
