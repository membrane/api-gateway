# Membrane API Gateway Tutorial - Rewriting OpenAPI Server URLs

When Membrane serves an OpenAPI document at `/api-docs`, it rewrites the `servers[].url`
entries so that Swagger UI and generated clients talk to the gateway instead of the
backend. This tutorial shows how that rewriting works and how to control it.

- Automatic rewriting: the served spec advertises the gateway without any configuration
- Explicit rewriting: pin the host, port and base path
- Behind a reverse proxy: override protocol and host for a public TLS endpoint

To begin, open [10-Automatic-Server-Rewriting.apis.yaml](10-Automatic-Server-Rewriting.apis.yaml)
and follow the instructions in the file.

## Further reading

The [*API Gateway Handbook*](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf)
explains OpenAPI rewriting in more depth:

- [§6.2 OpenAPI URL Rewriting](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf#page=65)
  — why the gateway rewrites the `servers` section.
- [§28.2 Rewriting of OpenAPI Addresses](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf#page=216)
  — the Membrane configuration for default and explicit rewriting, including
  deployments behind a reverse proxy.


