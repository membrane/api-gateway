# Membrane API Gateway

**Open-source API gateway that speaks both REST and SOAP.**

[![GitHub release](https://img.shields.io/github/v/release/membrane/api-gateway?display_name=tag)](https://github.com/membrane/api-gateway/releases/latest)
[![Docker Pulls](https://img.shields.io/docker/pulls/predic8/membrane)](https://hub.docker.com/r/predic8/membrane)
[![License](https://img.shields.io/github/license/membrane/api-gateway)](https://github.com/membrane/api-gateway/blob/master/distribution/router/LICENSE.txt)

<img src="https://raw.githubusercontent.com/membrane/api-gateway/master/docs/images/api-gateway-demo.gif" alt="Animated demo of Membrane API Gateway" width="800">

Deploy APIs directly from **OpenAPI**, secure them with **OAuth2** and **JWT**, or turn legacy **SOAP web services into JSON APIs**, all with a few lines of YAML. Lightweight, fast, and no database required.

## Quick Start

```bash
docker run --rm -it -p 2000:2000 predic8/membrane
```

Explore the sample APIs in your browser:

- http://localhost:2000 (Current time)
- http://localhost:2000/api-docs (API deployed from OpenAPI)
- http://localhost:2000/shop/v2/products

## Proxy Your First API

Create an `apis.yaml` file:

```yaml
api:
  port: 2000
  target:
    url: https://apibin.io
```

Run a container with the file mounted:

**Linux/macOS:**

```bash
docker run --rm -p 2000:2000 -v "$(pwd)/apis.yaml:/opt/membrane/conf/apis.yaml" predic8/membrane
```

**Windows PowerShell:**

```pwsh
docker run --rm -p 2000:2000 -v "${PWD}/apis.yaml:/opt/membrane/conf/apis.yaml" predic8/membrane
```

Requests to http://localhost:2000 are now forwarded to https://apibin.io.

## Using the Image

| Item                | Value                                                                     |
|---------------------|---------------------------------------------------------------------------|
| Installation        | `/opt/membrane`                                                           |
| Configuration       | `/opt/membrane/conf/apis.yaml` (or `proxies.xml`)                         |
| Other config file   | Append `-c <file>` to `docker run`, e.g. `... predic8/membrane -c /config/my-apis.yaml` |
| JVM options         | Environment variable `JAVA_OPTS`, e.g. `-e JAVA_OPTS="-Xmx1g"`             |
| User                | Runs as non-root user `membrane` (UID 1001)                               |
| Base image          | Eclipse Temurin JDK 25                                                    |
| Platforms           | `linux/amd64`, `linux/arm64`                                              |

Mount any files your configuration references (OpenAPI documents, WSDLs, keystores, ...) into the container as well, e.g. next to it in `/opt/membrane/conf/`.

### Tags

| Tag       | Description                                  |
|-----------|----------------------------------------------|
| `latest`  | Latest stable release                        |
| `7`       | Latest release of major version 7            |
| `7.7`     | Latest patch release of the 7.7 line         |
| `7.7.1`   | A specific release                           |

Pin a specific version in production. Images are signed with [cosign](https://github.com/sigstore/cosign).

# Why Membrane

**Native OpenAPI support** – Deploy APIs from OpenAPI descriptions and validate requests and responses against them. Supports OpenAPI 3.0, 3.1, and **3.2**.

**Legacy XML and Web Services** – Expose SOAP web services as JSON APIs without changing the backend. `wsdl2openapi` generates OpenAPI from WSDL and converts between JSON and XML. Use XPath and JSONPath for routing, filtering, and transformation.

**Protect APIs and legacy services** – Validate messages against OpenAPI, JSON Schema, XSD, and WSDL. Guard against malicious XML, JSON, JSON-RPC, and GraphQL payloads. Secure APIs with API keys, OAuth2, and JWT, and SOAP services with WS-Security, including XML signatures and encryption.

**AI Gateway** – Control which MCP tools clients may call, and centralize LLM provider keys with per-user token budgets and model restrictions.

**API orchestration** – Combine calls to multiple APIs in one flow with loops and conditions.

**Simple configuration, flexible extensions** – Configure routing, security, and transformations in YAML. For custom logic use Groovy, SpEL, JSONPath, XPath, or your own Java plugin.

**Observability** – Logging, Prometheus metrics, Grafana dashboards, and OpenTelemetry tracing.

## High Throughput, Small Footprint

Membrane stays fast with security and validation enabled. On a 16-vCPU Azure VM it achieved:

| Configuration                             | Requests/sec |
|-------------------------------------------|-------------:|
| Plain proxying                            |  **216,000** |
| Basic Auth + rate limiting + TLS          |  **196,000** |
| OpenAPI validation of every request       |  **167,000** |

See the [benchmark setup and results](https://www.membrane-api.io/api-gateway-performance.html).


# Documentation and Support

- [Getting started](https://www.membrane-api.io/getting-started.html)
- [Tutorials](https://www.membrane-api.io/api-gateway-tutorial.html)
- [Reference documentation](https://www.membrane-api.io/docs/current/api.html)
- [Source code on GitHub](https://github.com/membrane/api-gateway)
- Questions: [GitHub Discussions](https://github.com/membrane/api-gateway/discussions)
- Bugs: [GitHub Issues](https://github.com/membrane/api-gateway/issues)
- [Commercial support](https://www.membrane-api.io/api-gateway-pricing.html)
- [API Gateway eBook](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf) – free download, no registration

# License

Membrane API Gateway is licensed under the [Apache License 2.0](https://github.com/membrane/api-gateway/blob/master/distribution/router/LICENSE.txt).
