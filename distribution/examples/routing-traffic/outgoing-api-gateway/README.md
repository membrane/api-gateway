# Membrane as API Gateway for outgoing Traffic

This sample shows how to setup Membrane to act as an **outgoing API gateway** that sits between internal services and external APIs as a controlled exit point for API requests.

By routing all outgoing API traffic through Membrane, organizations gain visibility and control over what data leaves the internal network.

## Why Use an Outgoing Gateway?

An outgoing gateway enables:

- **Client authentication**  
  Ensure only authorized internal clients access external APIs.

- **Centralized logging and auditing**  
  Track who accessed which API and when.

- **Message validation**  
  Control the data leaving and entering your organization.

- **Protection against malicious payloads**  
  Validate and filter JSON and XML to reduce security risks.

However, outgoing gateways must behave differently from gateways handling inbound traffic. For example:

- `X-Forwarded-For` must **not** be set, to avoid leaking internal IPs.
- Sensitive headers like `Authorization`, `Cookie`, and API keys must be carefully filtered.

This example demonstrates a secure default configuration for such scenarios.


## Running the Example

1. **Navigate** to the `examples/routing-traffic/outgoing-api-gateway` directory.
2. **Start** Membrane by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Execute the following requests** (alternatively, use the `requests.http` file):

curl -v http://localhost:2000 -H "X-Api-Key: abc123" -H "User-Agent: secret" -H "Authorization: secret"


## Combing with other Membrane features

You can extend this setup with additional Membrane capabilities:

- Logging of requests and responses
- Request and response message validation
- Basic, API Key, JWT or OAuth2 authentication
- JSON and XML protection
- Rate limiting and quotas
- Message rewriting or transformation
- Data loss protection
- ...