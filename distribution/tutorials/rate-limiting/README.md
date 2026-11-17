# Rate Limiting Tutorial

Learn how to limit the number of requests a client may send in a period of time:
per client IP, per API key or per field of the request body, for all APIs at
once, and with different limits for different customers.

Each step is explained directly in the configuration file, which is also the
Membrane config you run.

1. [10-Rate-Limit-by-Client-IP.yaml](10-Rate-Limit-by-Client-IP.yaml) — allow every
   client a number of requests per minute and read the quota from the response headers.
2. [20-Global-Rate-Limit.yaml](20-Global-Rate-Limit.yaml) — apply one limit to all
   APIs and a stricter one to a single API.
3. [30-Rate-Limit-by-JSON-Field.yaml](30-Rate-Limit-by-JSON-Field.yaml) — count
   requests by a field of the JSON body, e.g. to protect a password reset.
4. [40-Tiered-Rate-Limits.yaml](40-Tiered-Rate-Limits.yaml) — give every API key
   its own quota, and keys with a premium scope a higher one.
