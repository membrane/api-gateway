### RATE LIMITER

The `RateLimiter` plugin helps prevent abuse or overload of your API by limiting the number of allowed requests within a defined interval. This ensures reliable service availability and protects your resources from excessive usage. By applying global or endpoint-specific rate limits, you gain flexibility and precise control over request management.

#### RUNNING THE EXAMPLE

In this example, the global rate limit is set to 10 requests per minute (per client IP), with an additional specific limit of 3 requests per 30 seconds based on the JSON field `user` for the `/reset-pwd` endpoint. To test this:

1. Start Membrane from this directory by executing `membrane.sh` or `membrane.cmd`.
2. Send requests to test global limit:
    - Execute `curl localhost:2000 -I` 11 times within one minute. The first 10 requests succeed; the 11th will be blocked with HTTP status `429`.
3. end requests to test endpoint-specific limit:
    - Execute `curl -X POST localhost:2010/reset-pwd -H "Content-Type: application/json" -d '{"user":"testuser"}'` 4 times within 30 seconds. The first 3 requests succeed; the 4th request will be blocked with HTTP status `429`.

#### HOW IT WORKS

The rate limiting is configured in the `proxies.xml` file:

- The `<global>` rate limiter restricts clients to 10 requests per minute based on their IP address.
- The `<api>` endpoint at port `2010` with the path `/reset-pwd` uses a more specific limit: it allows only 3 requests per 30 seconds per unique JSON `user` field value.

The `<rateLimiter>` element has two configurable parameters:

- `requestLimit`: Maximum allowed requests per interval.
- `requestLimitDuration`: Interval length in ISO-8601 format (`PTxS` for seconds, e.g., `PT30S` for 30 seconds).

---
See detailed reference:
- [rateLimiter Documentation](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/rateLimiter.htm)

