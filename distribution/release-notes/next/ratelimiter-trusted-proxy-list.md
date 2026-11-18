---
type: breaking
area: Plugins
---
- `rateLimiter`: `trustedProxyList` now has to contain the address of the proxy that connects to Membrane as well. The client address, by which requests are counted unless a `keyExpression` yields a key, is the first address that is not in the list, searched from that proxy backwards through `X-Forwarded-For`. For requests from other addresses, the connecting address is the client address and their `X-Forwarded-For` is ignored. Addresses are compared by value, so `::1` matches `0:0:0:0:0:0:0:1`. Before, the list was compared with the end of `X-Forwarded-For` without the connecting proxy, so a list with a single load balancer never took effect. `trustedProxyCount` now counts the proxy that connects to Membrane as well, e.g. 1 for a single load balancer; with 0, `X-Forwarded-For` is ignored. Before, the count excluded that proxy. See the migration guide.
