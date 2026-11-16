---
type: breaking
area: Plugins
---
- `redis` with `ssl: true`: the TLS connection to Redis now checks that the server certificate is issued for the configured `host`, which Jedis 8 does by default. Before, any certificate from a trusted CA was accepted. A connector that connects by IP address, or by a name the certificate does not list, now fails to connect. Use a `host` that the certificate names, or a certificate that names the `host`. [#3780](https://github.com/membrane/api-gateway/pull/3780)
