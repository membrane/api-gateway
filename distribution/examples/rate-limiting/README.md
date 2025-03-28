### RATE LIMITER

The `rateLimiter` helps you maintain stable and reliable API performance by limiting the number of allowed requests within a defined interval. This approach protects your services from resource overuse and contributes to consistent uptime. By applying global or endpoint-specific rate limits, you gain flexibility and precise control over request management.

#### RUNNING THE EXAMPLE

In this example, the global rate limit is set to 10 requests per minute (per client IP), with an additional specific limit of 3 requests per 30 seconds based on the JSON field `user` for the `/reset-pwd` endpoint. To test this:

1. Start Membrane from this directory by executing `membrane.sh` or `membrane.cmd`.
2. Send requests to test global limit:
    - Execute `curl localhost:2000 -I` 11 times within one minute. The first 10 requests succeed; the 11th will be blocked with HTTP status `429`.
3. end requests to test endpoint-specific limit:
    - Execute `curl -X POST localhost:2010/reset-pwd -H "Content-Type: application/json" -d '{"user":"testuser"}'` 4 times within 30 seconds. The first 3 requests succeed; the 4th request will be blocked with HTTP status `429`.

---
See:
- [rateLimiter Documentation](https://www.membrane-api.io/docs/current/rateLimiter.html)

